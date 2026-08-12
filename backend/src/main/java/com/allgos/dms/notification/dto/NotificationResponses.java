package com.allgos.dms.notification.dto;

import com.allgos.dms.notification.entity.Notification;
import java.time.Instant;
import java.util.UUID;

/** Response bodies for the notification endpoints. */
public final class NotificationResponses {

    /**
     * One notification as the bell and the list see it.
     *
     * <p>{@code entityRef} is the free-form pointer written when the row was created —
     * {@code "file:{uuid}"} or {@code "user:{uuid}"}. The web app turns it into a link; the server
     * deliberately does not build a URL, because routes are the client's business.
     */
    public record NotificationView(
            UUID id,
            String type,
            String title,
            String body,
            String entityRef,
            boolean read,
            Instant createdAt) {

        public static NotificationView from(Notification notification) {
            return new NotificationView(
                    notification.getId(),
                    notification.getType(),
                    notification.getTitle(),
                    notification.getBody(),
                    notification.getEntityRef(),
                    notification.isRead(),
                    notification.getCreatedAt());
        }
    }

    /** What the bell badge needs, and nothing else — it is polled far more often than the list. */
    public record UnreadCount(long unread) {}

    private NotificationResponses() {}
}
