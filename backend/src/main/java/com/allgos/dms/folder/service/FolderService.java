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

    private final FolderRepository folderRepository;
    private final DepartmentRepository departmentRepository;
    private final StoredFileRepository fileRepository;
    private final AuditService auditService;

    public FolderService(
            FolderRepository folderRepository,
            DepartmentRepository departmentRepository,
            StoredFileRepository fileRepository,
            AuditService auditService) {
        this.folderRepository = folderRepository;
        this.departmentRepository = departmentRepository;
        this.fileRepository = fileRepository;
        this.auditService = auditService;
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
