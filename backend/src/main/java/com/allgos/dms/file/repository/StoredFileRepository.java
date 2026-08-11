package com.allgos.dms.file.repository;

import com.allgos.dms.file.entity.StoredFile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every read here filters soft-deleted rows out; the deletions log is the only view that sees them. */
public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

    Optional<StoredFile> findByIdAndDeletedFalse(UUID id);

    List<StoredFile> findByFolderIdAndDeletedFalseOrderByCreatedAtDesc(UUID folderId);

    Page<StoredFile> findByFolderIdAndDeletedFalse(UUID folderId, Pageable pageable);

    Page<StoredFile> findByDepartmentIdAndDeletedFalse(UUID departmentId, Pageable pageable);

    Page<StoredFile> findByUploadedByIdAndDeletedFalse(UUID uploaderId, Pageable pageable);

    long countByFolderIdAndDeletedFalse(UUID folderId);

    long countByDepartmentIdAndDeletedFalse(UUID departmentId);
}
