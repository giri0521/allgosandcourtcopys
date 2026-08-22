package com.allgos.dms.phonebook.controller;

import com.allgos.dms.common.security.AuthenticatedUser;
import com.allgos.dms.phonebook.dto.PhonebookRequests;
import com.allgos.dms.phonebook.dto.PhonebookResponses.ContactView;
import com.allgos.dms.phonebook.service.PhonebookService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Maintaining the phonebook.
 *
 * <p>As with the other admin controllers, {@code @PreAuthorize} sits on the class so a method added
 * later cannot be left unguarded. Everyone reads the book; only an administrator writes to it,
 * because a wrong number in a shared directory is rung for months before anyone traces it.
 */
@RestController
@RequestMapping("/api/v1/admin/phonebook")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPhonebookController {

    private final PhonebookService phonebookService;

    public AdminPhonebookController(PhonebookService phonebookService) {
        this.phonebookService = phonebookService;
    }

    @PostMapping
    public ContactView create(
            @Valid @RequestBody PhonebookRequests.SaveContact request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return phonebookService.create(request, principal.user());
    }

    @PutMapping("/{contactId}")
    public ContactView update(
            @PathVariable UUID contactId,
            @Valid @RequestBody PhonebookRequests.SaveContact request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return phonebookService.update(contactId, request, principal.user());
    }

    @DeleteMapping("/{contactId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID contactId, @AuthenticationPrincipal AuthenticatedUser principal) {
        phonebookService.delete(contactId, principal.user());
        return ResponseEntity.noContent().build();
    }
}
