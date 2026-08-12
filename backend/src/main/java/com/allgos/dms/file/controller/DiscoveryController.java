package com.allgos.dms.file.controller;

import com.allgos.dms.common.dto.PageResponse;
import com.allgos.dms.common.exception.ApiException;
import com.allgos.dms.common.security.AuthenticatedUser;
import com.allgos.dms.file.dto.FileResponses.DownloadView;
import com.allgos.dms.file.dto.FileResponses.FileView;
import com.allgos.dms.file.dto.FileResponses.PreviewLink;
import com.allgos.dms.file.service.DiscoveryService;
import com.allgos.dms.folder.entity.FolderCategory;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Finding documents: search, preview, favourites and download history.
 *
 * <p>No {@code @PreAuthorize}, for the same reason as {@link FileController}: every approved user
 * may read every department (rule 2). The scoping that does exist — your favourites, your history —
 * is by the authenticated principal in the service, not by a parameter a caller could change.
 */
@RestController
@RequestMapping("/api/v1")
public class DiscoveryController {

    private static final int MAX_PAGE_SIZE = 100;

    private final DiscoveryService discoveryService;

    public DiscoveryController(DiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    // ------------------------------------------------------------------------ search

    /**
     * Global search across every department.
     *
     * @param q at least two characters
     * @param from inclusive lower bound on the upload date, as an ISO instant
     * @param to inclusive upper bound
     */
    @GetMapping("/files/search")
    public PageResponse<FileView> search(
            @RequestParam String q,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        return discoveryService.search(
                q, departmentId, parseCategory(category), from, to, principal.user(), pageable(page, size));
    }

    /** Newest documents across every department, for the home dashboard. */
    @GetMapping("/files/recent")
    public PageResponse<FileView> recent(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        return discoveryService.listRecent(principal.user(), pageable(page, size));
    }

    // ----------------------------------------------------------------------- preview

    /** An inline presigned URL. 400 for anything a browser would not render. */
    @GetMapping("/files/{fileId}/preview-link")
    public PreviewLink previewLink(
            @PathVariable UUID fileId, @AuthenticationPrincipal AuthenticatedUser principal) {
        return discoveryService.previewLink(fileId, principal.user());
    }

    // --------------------------------------------------------------------- favorites

    @PostMapping("/files/{fileId}/favorite")
    public FileView addFavorite(
            @PathVariable UUID fileId, @AuthenticationPrincipal AuthenticatedUser principal) {
        return discoveryService.addFavorite(fileId, principal.user());
    }

    @DeleteMapping("/files/{fileId}/favorite")
    public FileView removeFavorite(
            @PathVariable UUID fileId, @AuthenticationPrincipal AuthenticatedUser principal) {
        return discoveryService.removeFavorite(fileId, principal.user());
    }

    @GetMapping("/favorites")
    public PageResponse<FileView> favorites(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        return discoveryService.listFavorites(principal.user(), pageable(page, size));
    }

    // --------------------------------------------------------------------- downloads

    @GetMapping("/downloads")
    public PageResponse<DownloadView> downloads(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        return discoveryService.listDownloads(principal.user(), pageable(page, size));
    }

    // ----------------------------------------------------------------------- helpers

    /**
     * No Sort: each repository method here orders in its own query — the search by relevance-free
     * recency, the rest by when the row was created. Adding a Sort would append a second ORDER BY.
     */
    private Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
    }

    /** An unknown facet is refused rather than quietly ignored, which would silently widen a search. */
    private FolderCategory parseCategory(String category) {
        if (category == null || category.isBlank() || "all".equalsIgnoreCase(category)) {
            return null;
        }
        try {
            return FolderCategory.valueOf(category.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("CATEGORY_INVALID", "Unknown category: " + category);
        }
    }
}
