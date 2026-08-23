package com.allgos.dms.letter.controller;

import com.allgos.dms.common.dto.PageResponse;
import com.allgos.dms.common.security.AuthenticatedUser;
import com.allgos.dms.letter.dto.LetterRequests;
import com.allgos.dms.letter.dto.LetterResponses.LetterSummary;
import com.allgos.dms.letter.dto.LetterResponses.LetterView;
import com.allgos.dms.letter.dto.LetterResponses.TemplateView;
import com.allgos.dms.letter.service.LetterService;
import com.allgos.dms.letter.service.LetterTemplateService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Writing letters: the templates to start from, and the letters themselves.
 *
 * <p>Every method is scoped to the caller by the service, which queries by author id. There is no
 * endpoint that lists or opens somebody else's letter.
 */
@RestController
@RequestMapping("/api/v1/letters")
public class LetterController {

    private static final int MAX_PAGE_SIZE = 100;

    private final LetterService letterService;
    private final LetterTemplateService templateService;

    public LetterController(LetterService letterService, LetterTemplateService templateService) {
        this.letterService = letterService;
        this.templateService = templateService;
    }

    /** The templates on offer. Retired ones are not among them. */
    @GetMapping("/templates")
    public List<TemplateView> templates() {
        return templateService.listActive();
    }

    @GetMapping
    public PageResponse<LetterSummary> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        return letterService.listMine(principal.user(), pageable(page, size));
    }

    @GetMapping("/{letterId}")
    public LetterView get(
            @PathVariable UUID letterId, @AuthenticationPrincipal AuthenticatedUser principal) {
        return letterService.get(letterId, principal.user());
    }

    @PostMapping
    public LetterView create(
            @Valid @RequestBody LetterRequests.SaveLetter request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return letterService.create(request, principal.user());
    }

    @PutMapping("/{letterId}")
    public LetterView update(
            @PathVariable UUID letterId,
            @Valid @RequestBody LetterRequests.SaveLetter request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return letterService.update(letterId, request, principal.user());
    }

    @DeleteMapping("/{letterId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID letterId, @AuthenticationPrincipal AuthenticatedUser principal) {
        letterService.delete(letterId, principal.user());
        return ResponseEntity.noContent().build();
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
    }
}
