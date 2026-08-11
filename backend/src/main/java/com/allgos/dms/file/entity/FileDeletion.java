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
 * <p>It has its own id and timestamps rather than extending a base class because the columns are
 * {@code deleted_at} and {@code restored_at}, not created/updated.
 */
@Entity
@Table(name = "file_deletions")
@Getter
@Setter
@NoArgsConstructor
public class FileDeletion {

    @Id
    @GeneratedValue
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false)
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

    public boolean isRestored() {
        return restoredAt != null;
    }

    @PrePersist
    void onCreate() {
        deletedAt = Instant.now();
    }
}
