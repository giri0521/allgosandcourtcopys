package com.allgos.dms.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request bodies for the file endpoints. */
public final class FileRequests {

    /**
     * Rule 4: a deletion must say why.
     *
     * <p>{@code @NotBlank} rather than {@code @NotNull} on purpose — a reason of spaces is no reason
     * at all, and the notification every admin receives would otherwise be empty. Both cases fail
     * validation and return 400.
     */
    public record Delete(@NotBlank @Size(max = 1000) String reason) {}

    /** Creating a folder inside a department, optionally nested under another folder. */
    public record CreateFolder(
            @NotBlank @Size(max = 255) String name,
            String category,
            java.util.UUID parentFolderId) {}

    private FileRequests() {}
}
