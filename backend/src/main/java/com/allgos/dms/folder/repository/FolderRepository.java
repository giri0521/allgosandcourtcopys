package com.allgos.dms.folder.repository;

import com.allgos.dms.folder.entity.Folder;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FolderRepository extends JpaRepository<Folder, UUID> {

    List<Folder> findByDepartmentIdAndParentIsNullOrderByNameAsc(UUID departmentId);

    List<Folder> findByParentIdOrderByNameAsc(UUID parentId);

    List<Folder> findByDepartmentIdOrderByNameAsc(UUID departmentId);

    boolean existsByDepartmentIdAndParentIdAndNameIgnoreCase(UUID departmentId, UUID parentId, String name);

    boolean existsByDepartmentIdAndParentIsNullAndNameIgnoreCase(UUID departmentId, String name);

    /**
     * Adjusts the denormalised counter in the database rather than in memory, so two people
     * uploading to the same folder at once cannot each write back a count read before the other's
     * insert.
     */
    @Modifying
    @Query("UPDATE Folder f SET f.fileCount = f.fileCount + :delta WHERE f.id = :id")
    void adjustFileCount(@Param("id") UUID id, @Param("delta") int delta);
}
