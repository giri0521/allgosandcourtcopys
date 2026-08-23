package com.allgos.dms.letter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** Request bodies for templates and letters. */
public final class LetterRequests {

    /**
     * A template, as an administrator maintains it.
     *
     * <p>Only the name is required. A template that is nothing but a name is a blank letter with a
     * label, which is a perfectly reasonable thing for an office to want.
     */
    public record SaveTemplate(
            @NotBlank(message = "Give the template a name")
            @Size(max = 160)
            String name,

            @Size(max = 500)
            String description,

            @Size(max = 500)
            String defaultSubject,

            @Size(max = 20_000, message = "That is longer than a letter template needs to be")
            String body,

            @Size(max = 120)
            String salutation,

            /** Retired templates stay readable on the letters already written from them. */
            boolean active) {}

    /**
     * A letter, as its author writes it.
     *
     * <p>The From block is required even though the server could build one from the account: it is
     * editable per letter, so what the author actually approved is what gets stored — deriving it on
     * save would quietly discard their edit.
     */
    public record SaveLetter(
            /** Which template it started from; may be null for a letter written from nothing. */
            UUID templateId,

            @Size(max = 120)
            String referenceNo,

            LocalDate letterDate,

            @NotBlank(message = "The From address is required")
            @Size(max = 2_000)
            String fromBlock,

            @NotBlank(message = "Say who the letter is to")
            @Size(max = 4_000)
            String toBlock,

            @Size(max = 120)
            String salutation,

            @NotBlank(message = "A subject is required")
            @Size(max = 1_000)
            String subject,

            @Size(max = 4_000)
            String reference,

            @NotBlank(message = "The letter needs a body")
            @Size(max = 50_000)
            String body,

            @Size(max = 500)
            String enclosure,

            @Size(max = 4_000)
            String copyTo,

            @Size(max = 1_000)
            String signOff) {}

    private LetterRequests() {}
}
