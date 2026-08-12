package com.allgos.dms.file.repository;

import com.allgos.dms.file.entity.StoredFile;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every read here filters soft-deleted rows out; the deletions log is the only view that sees them. */
public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

    Optional<StoredFile> findByIdAndDeletedFalse(UUID id);

    List<StoredFile> findByFolderIdAndDeletedFalseOrderByCreatedAtDesc(UUID folderId);

    Page<StoredFile> findByFolderIdAndDeletedFalse(UUID folderId, Pageable pageable);

    Page<StoredFile> findByDepartmentIdAndDeletedFalse(UUID departmentId, Pageable pageable);

    Page<StoredFile> findByUploadedByIdAndDeletedFalse(UUID uploaderId, Pageable pageable);

    long countByFolderIdAndDeletedFalse(UUID folderId);

    long countByDepartmentIdAndDeletedFalse(UUID departmentId);

    /**
     * Global search by part of a document's name, narrowed by the optional facets.
     *
     * <p><b>Native, and deliberately so.</b> The schema carries a GIN trigram index on
     * {@code file_name} ({@code idx_files_name_trgm}), and {@code ILIKE '%…%'} is what uses it.
     * The JPQL equivalent would be {@code lower(fileName) like …}, which indexes
     * {@code lower(file_name)} — a different expression from the one the index was built on, so
     * every search would fall back to scanning the table. Rewriting this in JPQL would compile,
     * pass its tests on a handful of rows, and quietly stop scaling.
     *
     * <p>Every optional filter is wrapped in a {@code cast(… as …)}: a native query with a plain
     * null parameter leaves PostgreSQL unable to infer the type and it refuses to prepare the
     * statement. The casts are what allow one query to serve all sixteen filter combinations.
     *
     * <p>Search spans every department by choice, not oversight — rule 2 means an approved user may
     * read anything, so scoping results to the caller's own department would hide documents they are
     * entitled to. Soft-deleted rows are excluded, since a deleted document is gone to everything
     * except the admin deletions log.
     */
    @Query(
            value =
                    """
                    select f.* from files f
                    join folders fo on fo.id = f.folder_id
                    where f.is_deleted = false
                      and f.file_name ilike :pattern
                      and (cast(:departmentId as uuid) is null
                           or f.department_id = cast(:departmentId as uuid))
                      and (cast(:category as varchar) is null
                           or fo.category = cast(:category as varchar))
                      and (cast(:from as timestamptz) is null
                           or f.created_at >= cast(:from as timestamptz))
                      and (cast(:to as timestamptz) is null
                           or f.created_at <= cast(:to as timestamptz))
                    order by f.created_at desc
                    """,
            countQuery =
                    """
                    select count(*) from files f
                    join folders fo on fo.id = f.folder_id
                    where f.is_deleted = false
                      and f.file_name ilike :pattern
                      and (cast(:departmentId as uuid) is null
                           or f.department_id = cast(:departmentId as uuid))
                      and (cast(:category as varchar) is null
                           or fo.category = cast(:category as varchar))
                      and (cast(:from as timestamptz) is null
                           or f.created_at >= cast(:from as timestamptz))
                      and (cast(:to as timestamptz) is null
                           or f.created_at <= cast(:to as timestamptz))
                    """,
            nativeQuery = true)
    Page<StoredFile> search(
            @Param("pattern") String pattern,
            @Param("departmentId") String departmentId,
            @Param("category") String category,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    /** The home dashboard's "recently filed", across every department. */
    Page<StoredFile> findByDeletedFalse(Pageable pageable);

    long countByDeletedFalse();
}
