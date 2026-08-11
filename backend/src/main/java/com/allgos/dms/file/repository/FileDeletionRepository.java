package com.allgos.dms.file.repository;

import com.allgos.dms.file.entity.FileDeletion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileDeletionRepository extends JpaRepository<FileDeletion, UUID> {

    Page<FileDeletion> findAllBy(Pageable pageable);

    /** Only rows not yet restored — the "still deleted" tab of the admin log. */
    Page<FileDeletion> findByRestoredAtIsNull(Pageable pageable);

    /** The deletion a restore must stamp: the most recent unrestored one for this file. */
    Optional<FileDeletion> findFirstByFileIdAndRestoredAtIsNullOrderByDeletedAtDesc(UUID fileId);
}
