package com.allgos.dms.letter.repository;

import com.allgos.dms.letter.entity.Letter;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LetterRepository extends JpaRepository<Letter, UUID> {

    /** A person's own letters, newest first. */
    Page<Letter> findByAuthorIdOrderByCreatedAtDesc(UUID authorId, Pageable pageable);

    /**
     * Scoped by author in the query rather than checked after loading, so there is no arrangement of
     * parameters that returns somebody else's letter.
     */
    Optional<Letter> findByIdAndAuthorId(UUID id, UUID authorId);

    long countByTemplateId(UUID templateId);
}
