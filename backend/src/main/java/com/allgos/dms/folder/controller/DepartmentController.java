package com.allgos.dms.folder.controller;

import com.allgos.dms.common.security.AuthenticatedUser;
import com.allgos.dms.file.dto.FileRequests;
import com.allgos.dms.file.dto.FileResponses.DepartmentView;
import com.allgos.dms.file.dto.FileResponses.FolderView;
import com.allgos.dms.folder.service.FolderService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Browsing departments and their folders, for signed-in users.
 *
 * <p>Distinct from {@code PublicDepartmentController}, which returns only id and name to an
 * applicant who has no account yet. Everything here requires authentication — the security config
 * denies by default — but nothing here requires a particular department: rule 2 says every active
 * user browses all 43.
 */
@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {

    private final FolderService folderService;

    public DepartmentController(FolderService folderService) {
        this.folderService = folderService;
    }

    @GetMapping
    public List<DepartmentView> list() {
        return folderService.listDepartments();
    }

    /** @param parentFolderId omit for the folders at the department's top level */
    @GetMapping("/{departmentId}/folders")
    public List<FolderView> folders(
            @PathVariable UUID departmentId, @RequestParam(required = false) UUID parentFolderId) {
        return folderService.listFolders(departmentId, parentFolderId);
    }

    @PostMapping("/{departmentId}/folders")
    public FolderView createFolder(
            @PathVariable UUID departmentId,
            @Valid @RequestBody FileRequests.CreateFolder request,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        return folderService.create(
                departmentId, request.name(), request.category(), request.parentFolderId(), principal.user());
    }
}
