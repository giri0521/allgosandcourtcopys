package com.allgos.dms.letter.service;

import com.allgos.dms.audit.entity.AuditAction;
import com.allgos.dms.audit.service.AuditService;
import com.allgos.dms.common.dto.PageResponse;
import com.allgos.dms.common.exception.ApiException;
import com.allgos.dms.letter.dto.LetterRequests;
import com.allgos.dms.letter.dto.LetterResponses.LetterSummary;
import com.allgos.dms.letter.dto.LetterResponses.LetterView;
import com.allgos.dms.letter.entity.Letter;
import com.allgos.dms.letter.entity.LetterTemplate;
import com.allgos.dms.letter.repository.LetterRepository;
import com.allgos.dms.letter.repository.LetterTemplateRepository;
import com.allgos.dms.user.entity.User;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Letters somebody has written.
 *
 * <p><b>A letter belongs to its author.</b> Every method here is scoped by author id in the query
 * rather than filtered after loading, so there is no arrangement of parameters that reaches another
 * person's drafts — the same rule the notification list follows. An administrator who needs to know
 * what was issued reads the audit log, which records the act rather than copying the drafting.
 */
@Service
public class LetterService {

    private final LetterRepository letterRepository;
    private final LetterTemplateRepository templateRepository;
    private final AuditService auditService;

    public LetterService(
            LetterRepository letterRepository,
            LetterTemplateRepository templateRepository,
            AuditService auditService) {
        this.letterRepository = letterRepository;
        this.templateRepository = templateRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<LetterSummary> listMine(User author, Pageable pageable) {
        Page<Letter> page = letterRepository.findByAuthorIdOrderByCreatedAtDesc(author.getId(), pageable);
        return PageResponse.of(page, LetterSummary::from);
    }

    @Transactional(readOnly = true)
    public LetterView get(UUID letterId, User author) {
        return LetterView.from(require(letterId, author));
    }

    @Transactional
    public LetterView create(LetterRequests.SaveLetter request, User author) {
        Letter letter = new Letter();
        letter.setAuthor(author);
        apply(letter, request);
        letterRepository.save(letter);

        auditService.record(author, AuditAction.LETTER_CREATED, "letter", letter.getId(),
                Map.of("subject", letter.getSubject()));

        return LetterView.from(letter);
    }

    @Transactional
    public LetterView update(UUID letterId, LetterRequests.SaveLetter request, User author) {
        Letter letter = require(letterId, author);
        apply(letter, request);

        auditService.record(author, AuditAction.LETTER_UPDATED, "letter", letter.getId(),
                Map.of("subject", letter.getSubject()));

        return LetterView.from(letter);
    }

    @Transactional
    public void delete(UUID letterId, User author) {
        Letter letter = require(letterId, author);
        letterRepository.delete(letter);

        auditService.record(author, AuditAction.LETTER_DELETED, "letter", letterId,
                Map.of("subject", letter.getSubject()));
    }

    // ------------------------------------------------------------------------ helpers

    /**
     * Somebody else's letter reports 404 rather than 403: which ids exist is not information this
     * endpoint should confirm, and to this caller a letter they cannot open may as well not exist.
     */
    private Letter require(UUID letterId, User author) {
        return letterRepository
                .findByIdAndAuthorId(letterId, author.getId())
                .orElseThrow(() -> ApiException.notFound("Letter"));
    }

    private void apply(Letter letter, LetterRequests.SaveLetter request) {
        LetterTemplate template = request.templateId() == null
                ? null
                : templateRepository
                        .findById(request.templateId())
                        .orElseThrow(() -> ApiException.badRequest(
                                "TEMPLATE_INVALID", "That template no longer exists"));

        letter.setTemplate(template);
        letter.setReferenceNo(trimToNull(request.referenceNo()));
        letter.setLetterDate(request.letterDate());
        letter.setFromBlock(request.fromBlock().trim());
        letter.setToBlock(request.toBlock().trim());
        letter.setSalutation(trimToNull(request.salutation()));
        letter.setSubject(request.subject().trim());
        letter.setReference(trimToNull(request.reference()));
        letter.setBody(request.body().trim());
        letter.setEnclosure(trimToNull(request.enclosure()));
        letter.setCopyTo(trimToNull(request.copyTo()));
        letter.setSignOff(trimToNull(request.signOff()));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
