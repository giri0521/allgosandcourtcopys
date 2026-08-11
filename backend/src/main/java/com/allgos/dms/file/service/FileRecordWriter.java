package com.allgos.dms.file.service;

import com.allgos.dms.audit.entity.AuditAction;
import com.allgos.dms.audit.service.AuditService;
import com.allgos.dms.common.exception.ApiException;
import com.allgos.dms.file.dto.FileResponses.FileView;
import com.allgos.dms.file.entity.StoredFile;
import com.allgos.dms.file.repository.StoredFileRepository;
import com.allgos.dms.folder.entity.Folder;
import com.allgos.dms.folder.repository.FolderRepository;
import com.allgos.dms.user.entity.User;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Commits the database side of one upload, on its own.
 *
 * <p>A separate bean because the transaction boundary has to be crossed through the Spring proxy:
 * {@code FileService} uploads the bytes first and then calls this, and calling a
 * {@code @Transactional} method on {@code this} would silently run it in the caller's (absent)
 * transaction — the same trap {@code FailedAttemptRecorder} exists to avoid.
 *
 * <p>Each file commits by itself, so one failure in a multi-file upload does not undo the files that
 * already succeeded.
 */
@Component
public class FileRecordWriter {

    private final StoredFileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final AuditService auditService;

    public FileRecordWriter(
            StoredFileRepository fileRepository, FolderRepository folderRepository, AuditService auditService) {
        this.fileRepository = fileRepository;
        this.folderRepository = folderRepository;
        this.auditService = auditService;
    }

    /**
     * The facts {@code FileService} needs before it writes any bytes — read in a transaction so the
     * department behind the folder is a real row rather than a lazy proxy that would fail the moment
     * the request left the session.
     */
    @Transactional(readOnly = true)
    public FolderRef folderRef(UUID folderId) {
        Folder folder = folderRepository
                .findById(folderId)
                .orElseThrow(() -> ApiException.notFound("Folder"));
        return new FolderRef(folder.getId(), folder.getDepartment().getId());
    }

    /**
     * Writes the row and returns the view built <em>inside</em> this transaction. Returning the
     * entity instead would hand the caller lazy associations it has no session to resolve.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FileView record(UUID folderId, User uploader, UploadValidator.Accepted accepted, String storageKey) {

        Folder folder = folderRepository
                .findById(folderId)
                .orElseThrow(() -> ApiException.notFound("Folder"));

        StoredFile file = new StoredFile();
        file.setFolder(folder);
        // Denormalised from the folder, never from the request: the department a file belongs to is
        // decided by where it was filed, not by what the client claimed.
        file.setDepartment(folder.getDepartment());
        file.setFileName(accepted.fileName());
        file.setFileType(accepted.contentType());
        file.setSizeBytes(accepted.sizeBytes());
        file.setStorageKey(storageKey);
        file.setUploadedBy(uploader);

        StoredFile saved = fileRepository.save(file);
        folderRepository.adjustFileCount(folder.getId(), 1);

        auditService.record(
                uploader,
                AuditAction.FILE_UPLOADED,
                "file",
                saved.getId(),
                Map.of(
                        "fileName", saved.getFileName(),
                        "folderId", folder.getId().toString(),
                        "departmentId", folder.getDepartment().getId().toString(),
                        "sizeBytes", saved.getSizeBytes()));

        return FileView.from(saved, saved.canBeDeletedBy(uploader));
    }

    /** A folder and the department it belongs to, resolved eagerly. */
    public record FolderRef(UUID folderId, UUID departmentId) {}
}
