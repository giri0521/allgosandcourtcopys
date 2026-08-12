package com.allgos.dms.file.repository;

import com.allgos.dms.file.entity.Download;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DownloadRepository extends JpaRepository<Download, UUID> {

    /**
     * A user's own download history, newest first.
     *
     * <p>Unlike favourites, deleted files are <em>not</em> filtered out: the history is a record of
     * what happened, and hiding a row because the document was later removed would quietly rewrite
     * it. The screen marks such rows instead.
     */
    Page<Download> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserId(UUID userId);
}
