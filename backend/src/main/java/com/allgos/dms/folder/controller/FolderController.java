package com.allgos.dms.folder.controller;

import com.allgos.dms.common.dto.PageResponse;
import com.allgos.dms.common.security.AuthenticatedUser;
import com.allgos.dms.file.dto.FileResponses.FileView;
import com.allgos.dms.file.dto.FileResponses.FolderView;
import com.allgos.dms.file.service.FileService;
import com.allgos.dms.folder.service.FolderService;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** One folder: its details, its breadcrumb, its subfolders and the files inside it. */
@RestController
@RequestMapping("/api/v1/folders")
public class FolderController {

    private static final int MAX_PAGE_SIZE = 100;

    private final FolderService folderService;
    private final FileService fileService;

    public FolderController(FolderService folderService, FileService fileService) {
        this.folderService = folderService;
        this.fileService = fileService;
    }

    @GetMapping("/{folderId}")
    public FolderView get(@PathVariable UUID folderId) {
        return folderService.getFolder(folderId);
    }

    /** Root-first, so the web app can render it straight into a breadcrumb. */
    @GetMapping("/{folderId}/breadcrumb")
    public List<FolderView> breadcrumb(@PathVariable UUID folderId) {
        return folderService.breadcrumb(folderId);
    }

    @GetMapping("/{folderId}/folders")
    public List<FolderView> subfolders(@PathVariable UUID folderId) {
        return folderService.listFolders(folderService.getFolder(folderId).departmentId(), folderId);
    }

    @GetMapping("/{folderId}/files")
    public PageResponse<FileView> files(
            @PathVariable UUID folderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        return fileService.listByFolder(folderId, principal.user(), pageable(page, size));
    }

    /** Newest first, with the page size capped so one request cannot pull a whole department. */
    private Pageable pageable(int page, int size) {
        return PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
