package com.allgos.dms.letter.controller;

import com.allgos.dms.common.security.AuthenticatedUser;
import com.allgos.dms.letter.dto.LetterRequests;
import com.allgos.dms.letter.dto.LetterResponses.TemplateView;
import com.allgos.dms.letter.service.LetterTemplateService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Maintaining the letter templates.
 *
 * <p>As elsewhere, {@code @PreAuthorize} sits on the class so a method added later cannot be left
 * unguarded. The reading side lives on {@link LetterController}, where everybody can reach it.
 */
@RestController
@RequestMapping("/api/v1/admin/letter-templates")
@PreAuthorize("hasRole('ADMIN')")
public class AdminLetterTemplateController {

    private final LetterTemplateService templateService;

    public AdminLetterTemplateController(LetterTemplateService templateService) {
        this.templateService = templateService;
    }

    /** Includes retired templates, which is the only place they can be seen and revived. */
    @GetMapping
    public List<TemplateView> list() {
        return templateService.listAll();
    }

    @PostMapping
    public TemplateView create(
            @Valid @RequestBody LetterRequests.SaveTemplate request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return templateService.create(request, principal.user());
    }

    @PutMapping("/{templateId}")
    public TemplateView update(
            @PathVariable UUID templateId,
            @Valid @RequestBody LetterRequests.SaveTemplate request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return templateService.update(templateId, request, principal.user());
    }

    /**
     * Removes an unused template; retires one that letters have been written from.
     *
     * @return {@code removed} so the screen can say which of the two happened rather than claiming
     *     a deletion that did not occur
     */
    @DeleteMapping("/{templateId}")
    public Map<String, Boolean> delete(
            @PathVariable UUID templateId, @AuthenticationPrincipal AuthenticatedUser principal) {
        return Map.of("removed", templateService.deleteOrRetire(templateId, principal.user()));
    }
}
