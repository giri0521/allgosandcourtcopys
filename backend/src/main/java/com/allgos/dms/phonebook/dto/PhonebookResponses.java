package com.allgos.dms.phonebook.dto;

import com.allgos.dms.phonebook.entity.PhonebookContact;
import com.allgos.dms.phonebook.entity.PhonebookRole;
import java.util.List;
import java.util.UUID;

/** Response bodies for the phonebook. */
public final class PhonebookResponses {

    /**
     * One contact, as both books show it.
     *
     * <p>The same shape either way, with the fields the other book does not use left null — a
     * screen that renders a contact should not need to know which listing it came from.
     */
    public record ContactView(
            UUID id,
            String fullName,
            String designation,
            String phoneNumber,
            String alternatePhone,
            String email,
            UUID departmentId,
            String departmentName,
            String taluk,
            PhonebookRole role) {

        public static ContactView from(PhonebookContact contact) {
            return new ContactView(
                    contact.getId(),
                    contact.getFullName(),
                    contact.getDesignation(),
                    contact.getPhoneNumber(),
                    contact.getAlternatePhone(),
                    contact.getEmail(),
                    contact.getDepartment() == null ? null : contact.getDepartment().getId(),
                    contact.getDepartment() == null ? null : contact.getDepartment().getName(),
                    contact.getTaluk(),
                    contact.getRole());
        }
    }

    /** A department and everyone listed under it, in name order. */
    public record DepartmentContacts(UUID departmentId, String departmentName, List<ContactView> contacts) {}

    /**
     * A taluk, its tahsildars and its group members.
     *
     * <p>Split rather than one list with a role beside each name: somebody looking up a taluk is
     * nearly always after the tahsildar, and a screen should not have to filter to show that.
     */
    public record TalukContacts(String taluk, List<ContactView> tahsildars, List<ContactView> groupMembers) {}

    private PhonebookResponses() {}
}
