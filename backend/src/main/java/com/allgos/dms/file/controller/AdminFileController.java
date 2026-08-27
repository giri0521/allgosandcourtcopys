package com.allgos.dms.file.controller;

import com.allgos.dms.common.dto.PageResponse;
import com.allgos.dms.common.security.AuthenticatedUser;
import com.allgos.dms.file.dto.FileResponses.DeletionView;
import com.allgos.dms.file.dto.FileResponses.FileView;
import com.allgos.dms.file.service.FileService;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The deletions log and restore, for administrators.
 *
 * <p>As with {@code AdminUserController}, the {@code @PreAuthorize} sits on the class so a method
 * added later cannot be left unguarded by forgetting an annotation.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminFileController {

    private static final int MAX_PAGE_SIZE = 100;

    private final FileService fileService;

    public AdminFileController(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * @param status "deleted" for files still deleted, anything else (or omitted) for the full
     *     history including files that were restored
     */
    @GetMapping("/deletions")
    public PageResponse<DeletionView> deletions(
            @RequestParam(defaultValue = "deleted") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return fileService.listDeletions("deleted".equalsIgnoreCase(status), pageable(page, size));
    }

    @PostMapping("/files/{fileId}/restore")
    public FileView restore(
            @PathVariable UUID fileId, @AuthenticationPrincipal AuthenticatedUser principal) {
        return fileService.restore(fileId, principal.user());
    }

    /**
     * Permanently removes a deleted document rather than waiting out the 30-day retention window.
     * Irreversible — there is no restore from here on, which is why this is admin-only same as
     * restore is, and why the web app asks for a confirmation before calling it.
     */
    @PostMapping("/files/{fileId}/purge")
    public void purge(@PathVariable UUID fileId, @AuthenticationPrincipal AuthenticatedUser principal) {
        fileService.purge(fileId, principal.user());
    }

    /** Most recent deletion first. */
    private Pageable pageable(int page, int size) {
        return PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "deletedAt"));
    }
}
