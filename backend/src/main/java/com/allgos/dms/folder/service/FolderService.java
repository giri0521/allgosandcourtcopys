package com.allgos.dms.folder.service;

import com.allgos.dms.audit.entity.AuditAction;
import com.allgos.dms.audit.service.AuditService;
import com.allgos.dms.common.exception.ApiException;
import com.allgos.dms.department.entity.Department;
import com.allgos.dms.department.repository.DepartmentRepository;
import com.allgos.dms.file.dto.FileResponses.DepartmentView;
import com.allgos.dms.file.dto.FileResponses.FolderView;
import com.allgos.dms.file.repository.StoredFileRepository;
import com.allgos.dms.folder.entity.Folder;
import com.allgos.dms.folder.entity.FolderCategory;
import com.allgos.dms.folder.repository.FolderRepository;
import com.allgos.dms.notification.entity.NotificationType;
import com.allgos.dms.notification.service.NotificationService;
import com.allgos.dms.user.entity.User;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Browsing the department and folder tree, and creating folders.
 *
 * <p>Nothing here filters by the caller's own department: rule 2 makes all 43 departments readable
 * and writable by every active user, so the caller's identity matters only for the audit trail.
 */
@Service
public class FolderService {

    /** Seeded for every department by {@code V10__general_folder_per_department}; never deletable. */
    static final String GENERAL_FOLDER_NAME = "General";

    private final FolderRepository folderRepository;
    private final DepartmentRepository departmentRepository;
    private final StoredFileRepository fileRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public FolderService(
            FolderRepository folderRepository,
            DepartmentRepository departmentRepository,
            StoredFileRepository fileRepository,
            AuditService auditService,
            NotificationService notificationService) {
        this.folderRepository = folderRepository;
        this.departmentRepository = departmentRepository;
        this.fileRepository = fileRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    /** Every active department, with the counts the browse cards show. */
    @Transactional(readOnly = true)
    public List<DepartmentView> listDepartments() {
        return departmentRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(department -> new DepartmentView(
                        department.getId(),
                        department.getName(),
                        department.getCode(),
                        department.getDescription(),
                        folderRepository.findByDepartmentIdOrderByNameAsc(department.getId()).size(),
                        fileRepository.countByDepartmentIdAndDeletedFalse(department.getId())))
                .toList();
    }

    /**
     * The folders directly inside a department, or inside one of its folders.
     *
     * @param parentFolderId null for the department's top level
     */
    @Transactional(readOnly = true)
    public List<FolderView> listFolders(UUID departmentId, UUID parentFolderId) {
        requireDepartment(departmentId);

        List<Folder> folders = parentFolderId == null
                ? folderRepository.findByDepartmentIdAndParentIsNullOrderByNameAsc(departmentId)
                : folderRepository.findByParentIdOrderByNameAsc(parentFolderId);

        return folders.stream().map(FolderView::from).toList();
    }

    @Transactional(readOnly = true)
    public FolderView getFolder(UUID folderId) {
        return FolderView.from(loadFolder(folderId));
    }

    /** The chain from the department's root down to this folder, for the breadcrumb. */
    @Transactional(readOnly = true)
    public List<FolderView> breadcrumb(UUID folderId) {
        Folder folder = loadFolder(folderId);
        List<FolderView> trail = new java.util.ArrayList<>();

        for (Folder current = folder; current != null; current = current.getParent()) {
            trail.add(FolderView.from(current));
        }
        java.util.Collections.reverse(trail);
        return trail;
    }

    /**
     * Creates a folder. The unique constraint on (department, parent, name) is the real guard; the
     * pre-check exists only so the user gets a clear message instead of a constraint violation.
     */
    @Transactional
    public FolderView create(UUID departmentId, String name, String category, UUID parentFolderId, User actor) {
        Department department = requireDepartment(departmentId);
        String trimmed = name.trim();

        Folder parent = null;
        if (parentFolderId != null) {
            parent = loadFolder(parentFolderId);
            if (!parent.getDepartment().getId().equals(departmentId)) {
                throw ApiException.badRequest(
                        "PARENT_DEPARTMENT_MISMATCH", "That parent folder belongs to another department.");
            }
        }

        boolean exists = parent == null
                ? folderRepository.existsByDepartmentIdAndParentIsNullAndNameIgnoreCase(departmentId, trimmed)
                : folderRepository.existsByDepartmentIdAndParentIdAndNameIgnoreCase(
                        departmentId, parent.getId(), trimmed);
        if (exists) {
            throw ApiException.conflict("FOLDER_EXISTS", "A folder called \"%s\" is already here.".formatted(trimmed));
        }

        Folder folder = new Folder();
        folder.setDepartment(department);
        folder.setParent(parent);
        folder.setName(trimmed);
        folder.setCategory(parseCategory(category));
        folder.setCreatedBy(actor);

        Folder saved = folderRepository.save(folder);

        auditService.record(
                actor,
                AuditAction.FOLDER_CREATED,
                "folder",
                saved.getId(),
                Map.of("name", trimmed, "departmentId", departmentId.toString()));

        return FolderView.from(saved);
    }

    /**
     * Deletes a folder holding no live document and no subfolder. Admin-only — enforced by
     * {@code @PreAuthorize} on the controller, since unlike a file there is no owner to fall back to
     * and no restore once it is gone.
     *
     * <p>A folder that once held a document, since deleted, is not held back by that: the file's row
     * survives a soft delete so it can still be restored and so the admin deletions log can still say
     * where it came from, and {@code files.folder_id} is a {@code NOT NULL} foreign key with no
     * {@code ON DELETE} action — so before this folder is removed, any such row is moved to the
     * department's General folder instead, the same place an unfiled upload already lands by
     * convention. A restore from here on lands in General rather than a folder that no longer exists;
     * the deletions log's own record of where the document <em>was</em> filed is unaffected, since
     * that is captured on the deletion row itself, not read back through this association.
     *
     * <p>Subfolders are a different matter and still block this outright: a folder full of subfolders
     * that are themselves empty must be deleted leaf-first, since cascading would silently take
     * documents with it that nobody meant to remove in one click.
     *
     * @throws ApiException 400 if the reason is missing, or the folder is the department's General
     *     folder; 409 if the folder still holds a live document or a subfolder
     */
    @Transactional
    public void delete(UUID folderId, String reason, User actor) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty()) {
            throw ApiException.badRequest("REASON_REQUIRED", "Please give a reason for deleting this folder.");
        }

        Folder folder = loadFolder(folderId);

        if (folder.getParent() == null && GENERAL_FOLDER_NAME.equalsIgnoreCase(folder.getName())) {
            throw ApiException.badRequest(
                    "GENERAL_FOLDER", "The General folder cannot be deleted — every department needs one.");
        }
        if (fileRepository.countByFolderIdAndDeletedFalse(folder.getId()) > 0) {
            throw ApiException.conflict(
                    "FOLDER_NOT_EMPTY", "This folder still has documents in it. Delete or move them first.");
        }
        if (folderRepository.existsByParentId(folder.getId())) {
            throw ApiException.conflict(
                    "FOLDER_NOT_EMPTY", "This folder still has subfolders in it. Delete those first.");
        }

        UUID departmentId = folder.getDepartment().getId();
        String departmentName = folder.getDepartment().getName();
        String folderName = folder.getName();

        // Nothing live is left (checked above), so anything still pointing here is a soft-deleted
        // file kept only for restore and the deletions log — move it to General rather than leaving
        // it to block the delete with a foreign-key violation.
        if (fileRepository.existsByFolderId(folder.getId())) {
            Folder general = folderRepository
                    .findByDepartmentIdAndParentIsNullAndNameIgnoreCase(departmentId, GENERAL_FOLDER_NAME)
                    .orElseThrow(() -> ApiException.conflict(
                            "FOLDER_NOT_EMPTY",
                            "This folder still has a deleted document on record, and there is no General "
                                    + "folder here to move it to."));
            fileRepository.reassignFolder(folder.getId(), general.getId());
        }

        folderRepository.delete(folder);

        auditService.record(
                actor,
                AuditAction.FOLDER_DELETED,
                "folder",
                folderId,
                Map.of("name", folderName, "departmentId", departmentId.toString(), "reason", trimmed));

        notificationService.notifyEveryoneExcept(
                actor,
                NotificationType.FOLDER_DELETED,
                "A folder was deleted",
                "%s deleted the folder \"%s\" from %s. Reason: %s"
                        .formatted(actor.getFullName(), folderName, departmentName, trimmed),
                // The folder itself is gone; the department it sat in is still there to look at.
                "department:" + departmentId);
    }

    private static FolderCategory parseCategory(String category) {
        if (category == null || category.isBlank()) {
            return FolderCategory.GENERAL;
        }
        try {
            return FolderCategory.valueOf(category.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("CATEGORY_INVALID", "Unknown folder category: " + category);
        }
    }

    private Department requireDepartment(UUID departmentId) {
        return departmentRepository
                .findById(departmentId)
                .orElseThrow(() -> ApiException.notFound("Department"));
    }

    private Folder loadFolder(UUID folderId) {
        return folderRepository.findById(folderId).orElseThrow(() -> ApiException.notFound("Folder"));
    }
}
