package com.allgos.dms.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request bodies for the notification endpoints. */
public final class NotificationRequests {

    /**
     * A message to the whole office.
     *
     * <p>One field, deliberately. A subject line beside a body invites people to write the message
     * twice, and a notification is read in a list where only the first line is visible anyway — the
     * sender's name is the heading, and what they wrote is the message.
     *
     * <p>The 500-character ceiling is a notification's honest limit rather than an arbitrary
     * number: past that this is a document, and the system already has somewhere to put documents.
     */
    public record Announce(
            @NotBlank(message = "Write the message you want to send")
            @Size(max = 500, message = "Keep the message under 500 characters")
            String message) {}

    private NotificationRequests() {}
}
