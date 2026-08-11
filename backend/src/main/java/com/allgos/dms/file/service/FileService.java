package com.allgos.dms.file.service;

import com.allgos.dms.audit.entity.AuditAction;
import com.allgos.dms.audit.service.AuditService;
import com.allgos.dms.common.config.AppProperties;
import com.allgos.dms.common.dto.PageResponse;
import com.allgos.dms.common.exception.ApiException;
import com.allgos.dms.common.storage.StorageService;
import com.allgos.dms.file.dto.FileResponses.DeletionView;
import com.allgos.dms.file.dto.FileResponses.DownloadLink;
import com.allgos.dms.file.dto.FileResponses.FileView;
import com.allgos.dms.file.dto.FileResponses.UploadResult;
import com.allgos.dms.file.entity.FileDeletion;
import com.allgos.dms.file.entity.StoredFile;
import com.allgos.dms.file.repository.FileDeletionRepository;
import com.allgos.dms.file.repository.StoredFileRepository;
import com.allgos.dms.folder.entity.Folder;
import com.allgos.dms.folder.repository.FolderRepository;
import com.allgos.dms.notification.entity.NotificationType;
import com.allgos.dms.notification.service.NotificationService;
import com.allgos.dms.user.entity.User;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Uploading, reading, deleting and restoring documents.
 *
 * <p>Two rules from the specification are enforced here and nowhere else:
 *
 * <ul>
 *   <li><b>Rule 2 — uploads are not scoped to your own department.</b> Any active user may file a
 *       document into any department's folder. There is deliberately no check comparing the
 *       uploader's department against the folder's; adding one would be a regression, not a fix.
 *   <li><b>Rule 4 — deleting requires a reason</b>, the deleter must be the uploader or an admin,
 *       and every admin is notified with that reason. Ownership is re-read from the stored row, so
 *       nothing the client sends can influence the decision.
 * </ul>
 *
 * <h2>Ordering against object storage</h2>
 *
 * Object storage does not join the transaction, so one of the two failure modes has to be chosen.
 * The bytes are written <em>first</em> and the row second: a committed row therefore always has
 * bytes behind it, and the failure that remains is an orphaned object, which is invisible to users
 * and sweepable. The reverse order would let a row point at a document that cannot be downloaded.
 * When the row fails to commit, the object just written is deleted immediately.
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    private final StoredFileRepository fileRepository;
    private final FileDeletionRepository deletionRepository;
    private final FolderRepository folderRepository;
    private final StorageService storageService;
    private final UploadValidator uploadValidator;
    private final FileRecordWriter fileRecordWriter;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final AppProperties properties;

    public FileService(
            StoredFileRepository fileRepository,
            FileDeletionRepository deletionRepository,
            FolderRepository folderRepository,
            StorageService storageService,
            UploadValidator uploadValidator,
            FileRecordWriter fileRecordWriter,
            AuditService auditService,
            NotificationService notificationService,
            AppProperties properties) {
        this.fileRepository = fileRepository;
        this.deletionRepository = deletionRepository;
        this.folderRepository = folderRepository;
        this.storageService = storageService;
        this.uploadValidator = uploadValidator;
        this.fileRecordWriter = fileRecordWriter;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.properties = properties;
    }

    // ------------------------------------------------------------------------ upload

    /**
     * Uploads one or more files into a folder in any department.
     *
     * <p>Not {@code @Transactional}: each file is committed independently by
     * {@link FileRecordWriter} once its bytes are stored, so a rejected file never rolls back the
     * ones already accepted. The caller gets both lists back.
     */
    public UploadResult upload(UUID folderId, List<MultipartFile> parts, User uploader) {
        if (parts == null || parts.isEmpty()) {
            throw ApiException.badRequest("NO_FILES", "Select at least one file to upload.");
        }

        // Read in its own transaction: this method deliberately has none, so a lazy association
        // resolved later would fail with no session.
        FileRecordWriter.FolderRef folder = fileRecordWriter.folderRef(folderId);

        List<FileView> uploaded = new ArrayList<>();
        List<UploadResult.Rejected> rejected = new ArrayList<>();

        for (MultipartFile part : parts) {
            try {
                uploaded.add(uploadOne(folder, part, uploader));
            } catch (ApiException ex) {
                // One bad file does not fail the batch; the user is told exactly which and why.
                rejected.add(new UploadResult.Rejected(
                        UploadValidator.safeName(part.getOriginalFilename()), ex.getCode(), ex.getMessage()));
            }
        }

        return new UploadResult(uploaded, rejected);
    }

    private FileView uploadOne(FileRecordWriter.FolderRef folder, MultipartFile part, User uploader) {
        UploadValidator.Accepted accepted = uploadValidator.validate(part);
        uploadValidator.scanForMalware(part);

        String key = storageService.newKey(folder.departmentId(), folder.folderId(), accepted.fileName());

        InputStream content;
        try {
            content = part.getInputStream();
        } catch (IOException ex) {
            throw ApiException.badRequest(
                    "FILE_UNREADABLE", "%s could not be read.".formatted(accepted.fileName()));
        }

        try {
            storageService.put(key, content, accepted.contentType(), accepted.sizeBytes());
        } finally {
            StorageService.closeQuietly(content);
        }

        try {
            return fileRecordWriter.record(folder.folderId(), uploader, accepted, key);
        } catch (RuntimeException ex) {
            // The row did not commit, so nothing will ever reference these bytes.
            log.error("Recording upload {} failed; removing the stored object", accepted.fileName(), ex);
            storageService.delete(key);
            throw ex;
        }
    }

    // -------------------------------------------------------------------------- read

    @Transactional(readOnly = true)
    public PageResponse<FileView> listByFolder(UUID folderId, User viewer, Pageable pageable) {
        loadFolder(folderId); // 404 for an unknown folder rather than an empty page
        Page<StoredFile> page = fileRepository.findByFolderIdAndDeletedFalse(folderId, pageable);
        return PageResponse.of(page, file -> FileView.from(file, file.canBeDeletedBy(viewer)));
    }

    @Transactional(readOnly = true)
    public PageResponse<FileView> listMyUploads(User viewer, Pageable pageable) {
        Page<StoredFile> page = fileRepository.findByUploadedByIdAndDeletedFalse(viewer.getId(), pageable);
        return PageResponse.of(page, file -> FileView.from(file, file.canBeDeletedBy(viewer)));
    }

    @Transactional(readOnly = true)
    public FileView get(UUID fileId, User viewer) {
        StoredFile file = loadFile(fileId);
        return FileView.from(file, file.canBeDeletedBy(viewer));
    }

    /**
     * A short-lived presigned URL for the bytes.
     *
     * <p>Every active user may download from every department (rule 2), so the only checks are that
     * the file exists and has not been deleted.
     */
    @Transactional
    public DownloadLink downloadLink(UUID fileId, User viewer) {
        StoredFile file = loadFile(fileId);

        var ttl = properties.storage().presignedUrlTtl();
        String url = storageService.presignedGet(file.getStorageKey(), ttl, file.getFileName());

        auditService.record(
                viewer,
                AuditAction.FILE_DOWNLOADED,
                "file",
                file.getId(),
                Map.of("fileName", file.getFileName()));

        return new DownloadLink(url, Instant.now().plus(ttl), file.getFileName());
    }

    // ------------------------------------------------------------------------ delete

    /**
     * Soft-deletes a file, recording why and telling every admin.
     *
     * @throws ApiException 400 if the reason is missing or blank, 403 if the caller neither uploaded
     *     the file nor is an admin
     */
    @Transactional
    public void delete(UUID fileId, String reason, User actor) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty()) {
            // Rule 4. Without this the admin notification would carry nothing.
            throw ApiException.badRequest("REASON_REQUIRED", "Please give a reason for deleting this file.");
        }

        StoredFile file = loadFile(fileId);

        // Decided from the stored row, never from anything the client sent.
        if (!file.canBeDeletedBy(actor)) {
            auditService.recordDurable(
                    actor,
                    AuditAction.FILE_DELETED,
                    "file",
                    file.getId(),
                    Map.of("outcome", "refused", "reason", "not the uploader"));
            throw ApiException.forbidden(
                    "NOT_FILE_OWNER", "You can only delete files you uploaded. Ask an administrator.");
        }

        file.setDeleted(true);
        folderRepository.adjustFileCount(file.getFolder().getId(), -1);

        FileDeletion deletion = new FileDeletion();
        deletion.setFile(file);
        deletion.setDeletedBy(actor);
        deletion.setReason(trimmed);
        deletionRepository.save(deletion);

        auditService.record(
                actor,
                AuditAction.FILE_DELETED,
                "file",
                file.getId(),
                Map.of("fileName", file.getFileName(), "reason", trimmed));

        notificationService.notifyAllAdmins(
                NotificationType.FILE_DELETED,
                "A document was deleted",
                "%s deleted \"%s\" from %s. Reason: %s"
                        .formatted(
                                actor.getFullName(),
                                file.getFileName(),
                                file.getDepartment().getName(),
                                trimmed),
                "file:" + file.getId());
    }

    /** Puts a soft-deleted file back. Admin-only — the controller carries the role check. */
    @Transactional
    public FileView restore(UUID fileId, User admin) {
        StoredFile file = fileRepository.findById(fileId).orElseThrow(() -> ApiException.notFound("File"));

        if (!file.isDeleted()) {
            throw ApiException.conflict("FILE_NOT_DELETED", "That file has not been deleted.");
        }

        file.setDeleted(false);
        folderRepository.adjustFileCount(file.getFolder().getId(), 1);

        // Stamp the deletion that is being undone, so the log shows the round trip.
        deletionRepository
                .findFirstByFileIdAndRestoredAtIsNullOrderByDeletedAtDesc(fileId)
                .ifPresent(deletion -> {
                    deletion.setRestoredBy(admin);
                    deletion.setRestoredAt(Instant.now());
                });

        auditService.record(
                admin,
                AuditAction.FILE_RESTORED,
                "file",
                file.getId(),
                Map.of("fileName", file.getFileName()));

        notificationService.notify(
                file.getUploadedBy(),
                NotificationType.FILE_RESTORED,
                "Your document was restored",
                "An administrator restored \"%s\".".formatted(file.getFileName()),
                "file:" + file.getId());

        return FileView.from(file, file.canBeDeletedBy(admin));
    }

    /** The admin deletions log. {@code onlyUnrestored} narrows it to files still deleted. */
    @Transactional(readOnly = true)
    public PageResponse<DeletionView> listDeletions(boolean onlyUnrestored, Pageable pageable) {
        Page<FileDeletion> page = onlyUnrestored
                ? deletionRepository.findByRestoredAtIsNull(pageable)
                : deletionRepository.findAllBy(pageable);
        return PageResponse.of(page, DeletionView::from);
    }

    // ----------------------------------------------------------------------- helpers

    private Folder loadFolder(UUID folderId) {
        return folderRepository.findById(folderId).orElseThrow(() -> ApiException.notFound("Folder"));
    }

    /** A soft-deleted file is gone as far as every path except the deletions log is concerned. */
    private StoredFile loadFile(UUID fileId) {
        return fileRepository
                .findByIdAndDeletedFalse(fileId)
                .orElseThrow(() -> ApiException.notFound("File"));
    }
}
