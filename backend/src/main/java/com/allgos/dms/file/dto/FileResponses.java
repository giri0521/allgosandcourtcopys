package com.allgos.dms.file.dto;

import com.allgos.dms.file.entity.FileDeletion;
import com.allgos.dms.file.entity.StoredFile;
import com.allgos.dms.folder.entity.Folder;
import com.allgos.dms.folder.entity.FolderCategory;
import java.time.Instant;
import java.util.UUID;

/** Response bodies for the department, folder and file endpoints. */
public final class FileResponses {

    /** A department as the browsing screens see it, with enough counts to render a card. */
    public record DepartmentView(
            UUID id, String name, String code, String description, long folderCount, long fileCount) {}

    public record FolderView(
            UUID id,
            UUID departmentId,
            String departmentName,
            UUID parentFolderId,
            String name,
            FolderCategory category,
            int fileCount,
            Instant createdAt) {

        /** Must be called inside the transaction — department and parent are lazy. */
        public static FolderView from(Folder folder) {
            return new FolderView(
                    folder.getId(),
                    folder.getDepartment().getId(),
                    folder.getDepartment().getName(),
                    folder.getParent() == null ? null : folder.getParent().getId(),
                    folder.getName(),
                    folder.getCategory(),
                    folder.getFileCount(),
                    folder.getCreatedAt());
        }
    }

    /**
     * A file row.
     *
     * <p>{@code canModify} covers both deleting and replacing, which share one ownership rule, and
     * is a convenience for the UI only — the server re-checks on every such call, so a client that
     * ignores the flag gains nothing.
     */
    public record FileView(
            UUID id,
            UUID folderId,
            String folderName,
            UUID departmentId,
            String departmentName,
            String fileName,
            String fileType,
            long sizeBytes,
            UUID uploadedById,
            String uploadedByName,
            int version,
            boolean canModify,
            Instant uploadedAt) {

        public static FileView from(StoredFile file, boolean canModify) {
            return new FileView(
                    file.getId(),
                    file.getFolder().getId(),
                    file.getFolder().getName(),
                    file.getDepartment().getId(),
                    file.getDepartment().getName(),
                    file.getFileName(),
                    file.getFileType(),
                    file.getSizeBytes(),
                    file.getUploadedBy().getId(),
                    file.getUploadedBy().getFullName(),
                    file.getVersion(),
                    canModify,
                    file.getCreatedAt());
        }
    }

    /**
     * The outcome of one multipart upload.
     *
     * <p>Accepted and rejected files are reported together rather than failing the whole request,
     * so selecting eight documents and having one of them be a disguised executable still uploads
     * the other seven and says precisely what was wrong with the eighth.
     */
    public record UploadResult(java.util.List<FileView> uploaded, java.util.List<Rejected> rejected) {

        public record Rejected(String fileName, String code, String message) {}
    }

    /** A row in the admin deletions log, carrying the reason the deleter gave. */
    public record DeletionView(
            UUID id,
            UUID fileId,
            String fileName,
            UUID departmentId,
            String departmentName,
            UUID folderId,
            String folderName,
            String deletedByName,
            String reason,
            Instant deletedAt,
            String restoredByName,
            Instant restoredAt,
            boolean restorable) {

        public static DeletionView from(FileDeletion deletion) {
            StoredFile file = deletion.getFile();
            return new DeletionView(
                    deletion.getId(),
                    file.getId(),
                    file.getFileName(),
                    file.getDepartment().getId(),
                    file.getDepartment().getName(),
                    file.getFolder().getId(),
                    file.getFolder().getName(),
                    deletion.getDeletedBy().getFullName(),
                    deletion.getReason(),
                    deletion.getDeletedAt(),
                    deletion.getRestoredBy() == null ? null : deletion.getRestoredBy().getFullName(),
                    deletion.getRestoredAt(),
                    file.isDeleted());
        }
    }

    /** A short-lived presigned URL, plus when it stops working. */
    public record DownloadLink(String url, Instant expiresAt, String fileName) {}

    private FileResponses() {}
}
