package com.allgos.dms.folder.repository;

import com.allgos.dms.folder.entity.Folder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FolderRepository extends JpaRepository<Folder, UUID> {

    List<Folder> findByDepartmentIdAndParentIsNullOrderByNameAsc(UUID departmentId);

    List<Folder> findByParentIdOrderByNameAsc(UUID parentId);

    /** Whether a folder has any subfolders — a folder may only be deleted once this is false. */
    boolean existsByParentId(UUID parentId);

    List<Folder> findByDepartmentIdOrderByNameAsc(UUID departmentId);

    boolean existsByDepartmentIdAndParentIdAndNameIgnoreCase(UUID departmentId, UUID parentId, String name);

    boolean existsByDepartmentIdAndParentIsNullAndNameIgnoreCase(UUID departmentId, String name);

    /** Looks up a root folder by name — used to find a department's "General" catch-all, seeded for every department. */
    Optional<Folder> findByDepartmentIdAndParentIsNullAndNameIgnoreCase(UUID departmentId, String name);

    /**
     * Adjusts the denormalised counter in the database rather than in memory, so two people
     * uploading to the same folder at once cannot each write back a count read before the other's
     * insert.
     *
     * <p>Also stamps {@code updatedAt}, which a bulk update would otherwise skip — Hibernate's
     * {@code @PreUpdate} only fires for entity-level saves. A folder's "last modified" is meant to
     * include documents arriving in it, not only its own name or category changing, since that is
     * what a folder picker sorted by recency is for.
     */
    @Modifying
    @Query("UPDATE Folder f SET f.fileCount = f.fileCount + :delta, f.updatedAt = CURRENT_TIMESTAMP WHERE f.id = :id")
    void adjustFileCount(@Param("id") UUID id, @Param("delta") int delta);
}
