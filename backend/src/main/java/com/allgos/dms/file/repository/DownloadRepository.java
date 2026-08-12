package com.allgos.dms.file.repository;

import com.allgos.dms.file.entity.Download;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to);

    /**
     * Downloads per department in a period, reached through the document.
     *
     * <p>The department is denormalised onto the file, so this is one join rather than two — see
     * {@code StoredFile.department}.
     */
    @Query("""
            select d.file.department.id as departmentId, count(d) as total
            from Download d
            where d.createdAt >= :from and d.createdAt < :to
            group by d.file.department.id
            """)
    List<StoredFileRepository.DepartmentCount> countByDepartment(
            @Param("from") Instant from, @Param("to") Instant to);

    /** Downloads per calendar month, in Asia/Kolkata — see the note on the uploads equivalent. */
    @Query(
            value =
                    """
                    select to_char(date_trunc('month', created_at at time zone 'Asia/Kolkata'), 'YYYY-MM') as month,
                           count(*) as total
                    from downloads
                    where created_at >= :from and created_at < :to
                    group by 1
                    order by 1
                    """,
            nativeQuery = true)
    List<StoredFileRepository.MonthCount> countByMonth(@Param("from") Instant from, @Param("to") Instant to);
}
