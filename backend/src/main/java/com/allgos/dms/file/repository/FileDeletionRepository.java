package com.allgos.dms.file.repository;

import com.allgos.dms.file.entity.FileDeletion;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FileDeletionRepository extends JpaRepository<FileDeletion, UUID> {

    Page<FileDeletion> findAllBy(Pageable pageable);

    /** Only rows not yet restored — the "still deleted" tab of the admin log. Purged rows included. */
    Page<FileDeletion> findByRestoredAtIsNull(Pageable pageable);

    /** The deletion a restore, or a purge, must stamp: the most recent unrestored one for this file. */
    Optional<FileDeletion> findFirstByFileIdAndRestoredAtIsNullOrderByDeletedAtDesc(UUID fileId);

    /** How many documents this member has removed — a line on their activity summary. */
    long countByDeletedById(UUID userId);

    /** Removed and not put back — purged or not. A restored document is not a deleted one. */
    long countByRestoredAtIsNull();

    /**
     * The files the daily sweep should purge: still deleted, not yet purged, older than the
     * retention window. Selects just the id rather than the entity — {@code file} is lazy, and this
     * method runs with no open session by the time the caller iterates the result.
     */
    @Query(
            "select fd.file.id from FileDeletion fd "
                    + "where fd.restoredAt is null and fd.purgedAt is null "
                    + "and fd.deletedAt < :threshold and fd.file is not null")
    List<UUID> findPurgeableFileIds(@Param("threshold") Instant threshold);
}
