package com.allgos.dms.file.service;

import com.allgos.dms.common.config.AppProperties;
import com.allgos.dms.common.exception.ApiException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Decides whether an uploaded file is allowed in, before a single byte reaches object storage.
 *
 * <p>The declared content type is not trusted: a browser will send whatever it likes and an attacker
 * will send whatever gets through. Three things must agree — the extension, the type the bytes
 * actually are (detected from their signature by Tika), and the configured allow-list. A Windows
 * executable renamed to {@code .pdf} fails the second check and is refused.
 *
 * <p>The filename is also rebuilt rather than accepted: path separators and traversal segments are
 * stripped, so a name like {@code ../../etc/passwd} cannot influence anything downstream.
 */
@Component
public class UploadValidator {

    /** 50 MB, matching spring.servlet.multipart.max-file-size in application.yml. */
    private static final long MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024;

    private static final int MAX_FILENAME_LENGTH = 200;

    /**
     * Extension to the content types whose signature is acceptable for it.
     *
     * <p>The Office formats are ZIP containers, so Tika may report the generic ZIP type when the
     * bytes alone are ambiguous; both are listed rather than loosening the check to "anything".
     */
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "pdf", Set.of("application/pdf"),
            "jpg", Set.of("image/jpeg"),
            "jpeg", Set.of("image/jpeg"),
            "png", Set.of("image/png"),
            "tif", Set.of("image/tiff"),
            "tiff", Set.of("image/tiff"),
            "doc", Set.of("application/msword", "application/x-tika-msoffice"),
            "docx", Set.of(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/x-tika-ooxml",
                    "application/zip"),
            "xls", Set.of("application/vnd.ms-excel", "application/x-tika-msoffice"),
            "xlsx", Set.of(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/x-tika-ooxml",
                    "application/zip"));

    private final Tika tika = new Tika();
    private final Set<String> configuredContentTypes;

    public UploadValidator(AppProperties properties) {
        this.configuredContentTypes = Set.copyOf(properties.upload().allowedContentTypes());
    }

    /**
     * Validates one part of a multipart upload.
     *
     * @return the details to persist — a safe filename and the <em>detected</em> content type, which
     *     is what the row stores so a later download cannot be tricked into a different handler
     */
    public Accepted validate(MultipartFile part) {
        if (part == null || part.isEmpty()) {
            throw ApiException.badRequest("FILE_EMPTY", "One of the selected files is empty.");
        }
        if (part.getSize() > MAX_FILE_SIZE_BYTES) {
            throw ApiException.badRequest(
                    "FILE_TOO_LARGE",
                    "%s is larger than the 50 MB limit.".formatted(safeName(part.getOriginalFilename())));
        }

        String fileName = safeName(part.getOriginalFilename());
        String extension = extensionOf(fileName);

        Set<String> acceptableTypes = ALLOWED.get(extension);
        if (acceptableTypes == null) {
            throw ApiException.badRequest(
                    "FILE_TYPE_NOT_ALLOWED",
                    "%s is not an accepted document type. Allowed: PDF, JPG, PNG, TIFF, DOC, DOCX, XLS, XLSX."
                            .formatted(fileName));
        }

        String detected = detect(part, fileName);
        if (!acceptableTypes.contains(detected)) {
            // The extension and the bytes disagree — the usual shape of a disguised upload.
            throw ApiException.badRequest(
                    "FILE_CONTENT_MISMATCH",
                    "The contents of %s do not match its .%s extension.".formatted(fileName, extension));
        }

        String storedType = canonicalType(extension, detected);
        if (!configuredContentTypes.isEmpty() && !configuredContentTypes.contains(storedType)) {
            throw ApiException.badRequest(
                    "FILE_TYPE_NOT_ALLOWED", "%s is not an accepted document type.".formatted(fileName));
        }

        return new Accepted(fileName, storedType, part.getSize());
    }

    /**
     * Where a virus scan goes.
     *
     * <p>Deliberately a separate step from {@link #validate}: procurement has not chosen a scanner
     * yet, and when one arrives it will be an out-of-process call that needs its own failure
     * handling and timeout, not another branch inside the type checks.
     */
    public void scanForMalware(MultipartFile part) {
        // No scanner is wired yet. Implement here, and fail the upload with a 422 on a detection.
    }

    private String detect(MultipartFile part, String fileName) {
        // Tika needs to look at the first bytes; a buffered stream lets it reset afterwards.
        try (InputStream stream = new BufferedInputStream(part.getInputStream())) {
            return tika.detect(stream, fileName);
        } catch (IOException ex) {
            throw ApiException.badRequest("FILE_UNREADABLE", "%s could not be read.".formatted(fileName));
        }
    }

    /**
     * Tika reports the container type for the Office formats when the bytes are ambiguous. The row
     * stores the type the extension implies in that case, so downloads carry a usable header.
     */
    private static String canonicalType(String extension, String detected) {
        return switch (extension) {
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "doc" -> "application/msword";
            case "xls" -> "application/vnd.ms-excel";
            default -> detected;
        };
    }

    /** Strips any directory component and anything that could travel, then bounds the length. */
    static String safeName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "document";
        }
        String name = originalFilename.replace('\\', '/');
        name = Paths.get(name).getFileName().toString();
        name = name.replaceAll("[\\p{Cntrl}]", "").replaceAll("[/:*?\"<>|]", "_").trim();

        if (name.isBlank() || name.equals(".") || name.equals("..")) {
            return "document";
        }
        return name.length() > MAX_FILENAME_LENGTH ? name.substring(name.length() - MAX_FILENAME_LENGTH) : name;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** The validated facts about one part: what to call it, what it really is, and how big. */
    public record Accepted(String fileName, String contentType, long sizeBytes) {}
}
