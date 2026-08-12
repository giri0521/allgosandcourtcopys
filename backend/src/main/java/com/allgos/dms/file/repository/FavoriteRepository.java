package com.allgos.dms.file.repository;

import com.allgos.dms.file.entity.Favorite;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FavoriteRepository extends JpaRepository<Favorite, UUID> {

    Optional<Favorite> findByUserIdAndFileId(UUID userId, UUID fileId);

    /** A restored file reappears here; a still-deleted one must not. */
    Page<Favorite> findByUserIdAndFileDeletedFalseOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Which of these files this user has starred.
     *
     * <p>One query for a whole page rather than a lookup per row: without it, every file list would
     * fire a query per file just to decide whether to fill in a star.
     */
    @Query("select f.file.id from Favorite f where f.user.id = :userId and f.file.id in :fileIds")
    List<UUID> findFileIdsFor(@Param("userId") UUID userId, @Param("fileIds") Collection<UUID> fileIds);

    long countByUserIdAndFileDeletedFalse(UUID userId);
}
