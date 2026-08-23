package com.allgos.dms.letter.repository;

import com.allgos.dms.letter.entity.LetterTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LetterTemplateRepository extends JpaRepository<LetterTemplate, UUID> {

    /** What a writer chooses from: the ones still in use, in the order they are displayed. */
    List<LetterTemplate> findByActiveTrueOrderByNameAsc();

    /** The admin list, which shows retired templates too so they can be brought back. */
    List<LetterTemplate> findAllByOrderByNameAsc();

    Optional<LetterTemplate> findByNameIgnoreCase(String name);
}
