package com.allgos.dms.phonebook.dto;

import com.allgos.dms.phonebook.entity.PhonebookKind;
import com.allgos.dms.phonebook.entity.PhonebookRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Request bodies for maintaining the phonebook. */
public final class PhonebookRequests {

    /**
     * A telephone number as somebody would write it on paper.
     *
     * <p>Deliberately not the login pattern. That one demands ten digits starting 6-9, which is a
     * mobile — and the numbers most worth having in a phonebook are office landlines with an STD
     * code, sometimes an extension. This asks only that it be plausible: digits, with the
     * separators people actually type, and at least six of them.
     */
    private static final String PHONE_PATTERN = "^[0-9+][0-9 ()+\\-]{5,24}$";

    private static final String PHONE_MESSAGE = "Enter a phone number, e.g. 044-2345 6789 or 98765 43210";

    /**
     * The same, or nothing at all.
     *
     * <p>{@code @Pattern} passes a null but rejects an empty string, and a form that posts every
     * field it has will send "" for one the user left alone — which would refuse the whole contact
     * over a box nobody was asked to fill.
     */
    private static final String OPTIONAL_PHONE_PATTERN = "^$|" + PHONE_PATTERN;

    /**
     * One contact, for both books.
     *
     * <p>Which of {@code departmentId}, {@code taluk} and {@code role} are required depends on
     * {@code kind}, so that pairing is checked in the service where the rule can be explained in a
     * message, rather than here where it would only be expressible as a cross-field annotation
     * nobody reads.
     */
    public record SaveContact(
            @NotNull(message = "Choose which book this belongs to")
            PhonebookKind kind,

            @NotBlank(message = "Name is required")
            @Size(max = 255)
            String fullName,

            @Size(max = 255)
            String designation,

            @NotBlank(message = "A phone number is required")
            @Pattern(regexp = PHONE_PATTERN, message = PHONE_MESSAGE)
            String phoneNumber,

            @Pattern(regexp = OPTIONAL_PHONE_PATTERN, message = PHONE_MESSAGE)
            String alternatePhone,

            @Email(message = "Enter a valid email address")
            @Size(max = 255)
            String email,

            /** Optional in both books; the district the contact sits in. */
            @Size(max = 120)
            String district,

            /** Department book only. */
            UUID departmentId,

            /** Taluk book only. */
            @Size(max = 120)
            String taluk,

            /** Taluk book only. */
            PhonebookRole role) {}

    private PhonebookRequests() {}
}
