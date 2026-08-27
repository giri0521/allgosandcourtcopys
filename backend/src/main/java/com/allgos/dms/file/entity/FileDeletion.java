package com.allgos.dms.file.entity;

import com.allgos.dms.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The record of one deletion, and the reason given for it.
 *
 * <p>This is the audit the client actually asked for: every admin is notified with this reason at
 * the moment of deletion, and the deletions log reads these rows back. A restore stamps the same
 * row rather than deleting it, so the history of a file that was removed and put back stays intact.
 *
 * <p>The file's own identity — name, type, size, and where it was filed — is captured onto this row
 * at the moment of deletion, rather than read through {@link #file} each time the log is shown. Two
 * things need that: a deleted file may be purged, permanently, either by an admin or by the sweep
 * that runs once {@link #PURGE_RETENTION} has passed, and {@link #file} then becomes null; and even
 * before a purge, nothing here should break if the department or folder a file sat in is itself
 * later renamed or removed.
 *
 * <p>It has its own id and timestamps rather than extending a base class because the columns are
 * {@code deleted_at} and {@code restored_at}, not created/updated.
 */
@Entity
@Table(name = "file_deletions")
@Getter
@Setter
@NoArgsConstructor
public class FileDeletion {

    /** How long a deleted document stays recoverable before the daily sweep purges it for good. */
    public static final Duration PURGE_RETENTION = Duration.ofDays(30);

    @Id
    @GeneratedValue
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** Null once the file has been purged — the row this deletion was ever about is then gone. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id")
    private StoredFile file;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deleted_by", nullable = false)
    private User deletedBy;

    /** Required, never blank — the endpoint rejects the request before this row is built. */
    @Column(nullable = false)
    private String reason;

    @Column(name = "deleted_at", nullable = false, updatable = false)
    private Instant deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restored_by")
    private User restoredBy;

    @Column(name = "restored_at")
    private Instant restoredAt;

    // -------------------------------------------------------------- captured at the moment of delete

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_type", nullable = false)
    private String fileType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "department_name", nullable = false)
    private String departmentName;

    @Column(name = "folder_id", nullable = false)
    private UUID folderId;

    @Column(name = "folder_name", nullable = false)
    private String folderName;

    // ------------------------------------------------------------------------------------- purging

    /** Null for the automatic sweep — nobody pressed the button, 30 days did. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purged_by")
    private User purgedBy;

    @Column(name = "purged_at")
    private Instant purgedAt;

    public boolean isRestored() {
        return restoredAt != null;
    }

    public boolean isPurged() {
        return purgedAt != null;
    }

    /** Neither put back nor permanently removed yet — the only state a restore or a purge applies to. */
    public boolean isRestorable() {
        return restoredAt == null && purgedAt == null;
    }

    @PrePersist
    void onCreate() {
        deletedAt = Instant.now();
    }
}
