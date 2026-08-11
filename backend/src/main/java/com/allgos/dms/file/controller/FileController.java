package com.allgos.dms.file.controller;

import com.allgos.dms.common.dto.PageResponse;
import com.allgos.dms.common.security.AuthenticatedUser;
import com.allgos.dms.file.dto.FileRequests;
import com.allgos.dms.file.dto.FileResponses.DownloadLink;
import com.allgos.dms.file.dto.FileResponses.FileView;
import com.allgos.dms.file.dto.FileResponses.UploadResult;
import com.allgos.dms.file.service.FileService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Documents: upload, read, download and delete.
 *
 * <p>There is no {@code @PreAuthorize} here on purpose. Every active user may use all of it in every
 * department (rule 2), and the one restriction that does exist — you may only delete what you
 * uploaded, unless you are an admin — depends on the stored row, so it lives in the service where
 * the row is loaded rather than in an annotation.
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private static final int MAX_PAGE_SIZE = 100;

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * Uploads one or more files into any department's folder.
     *
     * <p>Returns 200 with both lists even when some files were rejected — see {@link UploadResult}.
     * A completely empty selection is the only thing that fails outright.
     */
    @PostMapping(consumes = "multipart/form-data")
    public UploadResult upload(
            @RequestParam UUID folderId,
            @RequestPart("files") List<MultipartFile> files,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        return fileService.upload(folderId, files, principal.user());
    }

    @GetMapping("/{fileId}")
    public FileView get(@PathVariable UUID fileId, @AuthenticationPrincipal AuthenticatedUser principal) {
        return fileService.get(fileId, principal.user());
    }

    /**
     * Replaces a document with a corrected version of it, keeping its id and bumping its version.
     *
     * <p>Single file rather than a list: this is one document being corrected, not a batch, and the
     * failure of the only file is the failure of the request — so unlike upload, a refusal here is a
     * 400 rather than a `rejected` entry.
     */
    @PostMapping(path = "/{fileId}/replace", consumes = "multipart/form-data")
    public FileView replace(
            @PathVariable UUID fileId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        return fileService.replace(fileId, file, principal.user());
    }

    /** A short-lived presigned URL; the bytes never pass through this server. */
    @GetMapping("/{fileId}/download-link")
    public DownloadLink downloadLink(
            @PathVariable UUID fileId, @AuthenticationPrincipal AuthenticatedUser principal) {
        return fileService.downloadLink(fileId, principal.user());
    }

    @GetMapping("/my-uploads")
    public PageResponse<FileView> myUploads(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        return fileService.listMyUploads(principal.user(), pageable(page, size));
    }

    /**
     * Soft-deletes a file. The reason is required and is sent to every admin.
     *
     * <p>A body on DELETE is unusual, but the reason belongs to the deletion rather than to the
     * resource path, and putting it in a query string would leak it into access logs.
     */
    @DeleteMapping("/{fileId}")
    public void delete(
            @PathVariable UUID fileId,
            @Valid @RequestBody FileRequests.Delete request,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        fileService.delete(fileId, request.reason(), principal.user());
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
