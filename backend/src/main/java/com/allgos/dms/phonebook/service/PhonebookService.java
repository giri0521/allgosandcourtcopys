package com.allgos.dms.phonebook.service;

import com.allgos.dms.audit.entity.AuditAction;
import com.allgos.dms.audit.service.AuditService;
import com.allgos.dms.common.exception.ApiException;
import com.allgos.dms.department.entity.Department;
import com.allgos.dms.department.repository.DepartmentRepository;
import com.allgos.dms.phonebook.dto.PhonebookRequests;
import com.allgos.dms.phonebook.dto.PhonebookResponses.ContactView;
import com.allgos.dms.phonebook.dto.PhonebookResponses.DepartmentContacts;
import com.allgos.dms.phonebook.dto.PhonebookResponses.TalukContacts;
import com.allgos.dms.phonebook.entity.PhonebookContact;
import com.allgos.dms.phonebook.entity.PhonebookKind;
import com.allgos.dms.phonebook.entity.PhonebookRole;
import com.allgos.dms.phonebook.repository.PhonebookContactRepository;
import com.allgos.dms.user.entity.User;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The office phonebook: two listings, and the admin maintenance behind them.
 *
 * <p>Reading is open to every approved user — a directory nobody can read is a filing cabinet — and
 * writing is administrators only, the same rule that governs departments and folders. One wrong
 * number in a book of two hundred is found slowly and rung often, so it is worth a gate.
 */
@Service
public class PhonebookService {

    private final PhonebookContactRepository contactRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditService auditService;

    public PhonebookService(
            PhonebookContactRepository contactRepository,
            DepartmentRepository departmentRepository,
            AuditService auditService) {
        this.contactRepository = contactRepository;
        this.departmentRepository = departmentRepository;
        this.auditService = auditService;
    }

    // --------------------------------------------------------------------------- read

    /**
     * The department book, grouped.
     *
     * <p>Grouped here rather than by the client: the order is part of the answer, and two screens
     * doing their own grouping is two chances to disagree about it.
     */
    @Transactional(readOnly = true)
    public List<DepartmentContacts> departmentBook() {
        Map<UUID, DepartmentContacts> byDepartment = new LinkedHashMap<>();

        for (PhonebookContact contact : contactRepository.departmentBook()) {
            Department department = contact.getDepartment();
            byDepartment
                    .computeIfAbsent(
                            department.getId(),
                            id -> new DepartmentContacts(id, department.getName(), new ArrayList<>()))
                    .contacts()
                    .add(ContactView.from(contact));
        }

        return List.copyOf(byDepartment.values());
    }

    /** The taluk book, grouped, with each taluk's tahsildars ahead of its group members. */
    @Transactional(readOnly = true)
    public List<TalukContacts> talukBook() {
        Map<String, TalukContacts> byTaluk = new LinkedHashMap<>();

        for (PhonebookContact contact : contactRepository.talukBook()) {
            TalukContacts taluk = byTaluk.computeIfAbsent(
                    contact.getTaluk(),
                    name -> new TalukContacts(name, new ArrayList<>(), new ArrayList<>()));

            if (contact.getRole() == PhonebookRole.TAHSILDAR) {
                taluk.tahsildars().add(ContactView.from(contact));
            } else {
                taluk.groupMembers().add(ContactView.from(contact));
            }
        }

        return List.copyOf(byTaluk.values());
    }

    // -------------------------------------------------------------------- maintenance

    @Transactional
    public ContactView create(PhonebookRequests.SaveContact request, User admin) {
        PhonebookContact contact = new PhonebookContact();
        contact.setCreatedBy(admin);
        apply(request, contact);

        PhonebookContact saved = contactRepository.save(contact);
        auditService.record(admin, AuditAction.PHONEBOOK_CONTACT_ADDED, "phonebook_contact", saved.getId(),
                Map.of("name", saved.getFullName(), "where", whereOf(saved)));

        return ContactView.from(saved);
    }

    @Transactional
    public ContactView update(UUID contactId, PhonebookRequests.SaveContact request, User admin) {
        PhonebookContact contact = contactRepository
                .findById(contactId)
                .orElseThrow(() -> ApiException.notFound("Contact"));

        apply(request, contact);

        auditService.record(admin, AuditAction.PHONEBOOK_CONTACT_UPDATED, "phonebook_contact", contact.getId(),
                Map.of("name", contact.getFullName(), "where", whereOf(contact)));

        return ContactView.from(contact);
    }

    @Transactional
    public void delete(UUID contactId, User admin) {
        PhonebookContact contact = contactRepository
                .findById(contactId)
                .orElseThrow(() -> ApiException.notFound("Contact"));

        // Recorded before the row goes, so the entry says who was removed rather than only that
        // something was.
        auditService.record(admin, AuditAction.PHONEBOOK_CONTACT_REMOVED, "phonebook_contact", contact.getId(),
                Map.of("name", contact.getFullName(), "where", whereOf(contact)));

        contactRepository.delete(contact);
    }

    // ------------------------------------------------------------------------ helpers

    /**
     * Copies a request onto a contact, enforcing the pairing between {@code kind} and the fields
     * that go with it.
     *
     * <p>The database has the same rule as a CHECK constraint. This is not duplication for its own
     * sake: the constraint stops a bad row existing, and this says why in words the person filling
     * the form can act on.
     */
    private void apply(PhonebookRequests.SaveContact request, PhonebookContact contact) {
        contact.setKind(request.kind());
        contact.setFullName(request.fullName().trim());
        contact.setDesignation(blankToNull(request.designation()));
        contact.setPhoneNumber(request.phoneNumber().trim());
        contact.setAlternatePhone(blankToNull(request.alternatePhone()));
        contact.setEmail(blankToNull(request.email()));
        contact.setDistrict(blankToNull(request.district()));

        if (request.kind() == PhonebookKind.DEPARTMENT) {
            if (request.departmentId() == null) {
                throw ApiException.badRequest("DEPARTMENT_REQUIRED", "Choose the department this contact belongs to.");
            }
            Department department = departmentRepository
                    .findById(request.departmentId())
                    .orElseThrow(() -> ApiException.badRequest("DEPARTMENT_INVALID", "Select a valid department"));

            contact.setDepartment(department);
            // Cleared rather than left: an entry moved from one book to the other must not keep the
            // other book's fields, or it fails the shape constraint on the way to the database.
            contact.setTaluk(null);
            contact.setRole(null);
            return;
        }

        if (blankToNull(request.taluk()) == null) {
            throw ApiException.badRequest("TALUK_REQUIRED", "Name the taluk this contact belongs to.");
        }
        if (request.role() == null) {
            throw ApiException.badRequest("ROLE_REQUIRED", "Say whether this is the tahsildar or a group member.");
        }

        contact.setTaluk(request.taluk().trim());
        contact.setRole(request.role());
        contact.setDepartment(null);
    }

    /** Where the contact sits, for the audit entry: a department name or a taluk. */
    private static String whereOf(PhonebookContact contact) {
        return contact.getKind() == PhonebookKind.DEPARTMENT
                ? contact.getDepartment().getName()
                : contact.getTaluk();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
