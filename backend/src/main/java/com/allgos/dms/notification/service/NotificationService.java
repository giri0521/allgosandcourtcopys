package com.allgos.dms.notification.service;

import com.allgos.dms.common.dto.PageResponse;
import com.allgos.dms.common.exception.ApiException;
import com.allgos.dms.notification.dto.NotificationResponses.NotificationView;
import com.allgos.dms.notification.entity.Notification;
import com.allgos.dms.notification.entity.NotificationType;
import com.allgos.dms.notification.repository.NotificationRepository;
import com.allgos.dms.user.entity.User;
import com.allgos.dms.user.entity.UserRole;
import com.allgos.dms.user.entity.UserStatus;
import com.allgos.dms.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void notify(User recipient, String type, String title, String body, String entityRef) {
        notificationRepository.save(build(recipient, type, title, body, entityRef));
    }

    private static Notification build(
            User recipient, String type, String title, String body, String entityRef) {
        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setEntityRef(entityRef);
        return notification;
    }

    /**
     * Fans a notification out to every active admin.
     *
     * <p>This is how a member's file deletion, with the reason they gave, reaches the administrators
     * — and how a new registration reaches the review queue.
     */
    @Transactional
    public void notifyAllAdmins(String type, String title, String body, String entityRef) {
        List<User> admins = userRepository.findByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE);
        admins.forEach(admin -> notify(admin, type, title, body, entityRef));
    }

    /**
     * Fans a notification out to the whole office — every active account except the one that caused
     * it. Admins and members alike: an upload is news to everyone, and the person who just did it
     * does not need telling.
     *
     * <p>Written in one {@code saveAll} rather than a save per recipient. With a hundred accounts
     * that is the difference between one statement batch and a hundred round trips on a request the
     * user is waiting on.
     */
    @Transactional
    public void notifyEveryoneExcept(User actor, String type, String title, String body, String entityRef) {
        List<Notification> notifications = userRepository.findByStatus(UserStatus.ACTIVE).stream()
                .filter(recipient -> !recipient.getId().equals(actor.getId()))
                .map(recipient -> build(recipient, type, title, body, entityRef))
                .toList();

        if (!notifications.isEmpty()) {
            notificationRepository.saveAll(notifications);
        }
    }

    public void notifyAdminsOfNewRegistration(User applicant) {
        notifyAllAdmins(
                NotificationType.REGISTRATION_SUBMITTED,
                "New registration awaiting approval",
                "%s (%s) has requested access."
                        .formatted(
                                applicant.getFullName(),
                                applicant.getDepartment() == null
                                        ? "no department"
                                        : applicant.getDepartment().getName()),
                "user:" + applicant.getId());
    }

    /** Sent the moment an admin approves; this is the applicant's cue that sign-in will now work. */
    public void notifyRegistrationApproved(User applicant) {
        notify(
                applicant,
                NotificationType.REGISTRATION_APPROVED,
                "Your account has been approved",
                "You can now sign in with your mobile number and password.",
                "user:" + applicant.getId());
    }

    /** Carries the reviewer's reason verbatim, so the applicant knows what to do about it. */
    public void notifyRegistrationRejected(User applicant, String reason) {
        notify(
                applicant,
                NotificationType.REGISTRATION_REJECTED,
                "Your registration was not approved",
                reason,
                "user:" + applicant.getId());
    }

    public void notifyAccountDisabled(User user) {
        notify(
                user,
                NotificationType.ACCOUNT_DISABLED,
                "Your account has been disabled",
                "An administrator has disabled your account. Contact your administrator to restore access.",
                "user:" + user.getId());
    }

    // -------------------------------------------------------------------- read side

    /**
     * A user's own notifications, newest first.
     *
     * <p>Scoped to the caller by user id rather than filtered afterwards, so there is no arrangement
     * of parameters that returns somebody else's.
     *
     * @param unreadOnly the "Unread" filter on the notifications screen
     */
    @Transactional(readOnly = true)
    public PageResponse<NotificationView> list(User user, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(user.getId(), pageable)
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);

        return PageResponse.of(page, NotificationView::from);
    }

    /**
     * Marks one notification read.
     *
     * <p>Someone else's notification reports 404 rather than 403: which ids exist is not information
     * this endpoint should confirm, and to the caller a row they cannot touch may as well not exist.
     * Marking an already-read one again is a no-op rather than an error — the bell fires this on
     * click, and a double click is not a mistake worth reporting.
     */
    @Transactional
    public NotificationView markRead(User user, UUID notificationId) {
        Notification notification = notificationRepository
                .findById(notificationId)
                .filter(candidate -> candidate.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> ApiException.notFound("Notification"));

        notification.setRead(true);
        return NotificationView.from(notification);
    }

    /** @return how many were still unread, so the UI can say "12 marked as read" */
    @Transactional
    public int markAllRead(User user) {
        return notificationRepository.markAllReadFor(user.getId());
    }

    @Transactional(readOnly = true)
    public long unreadCount(User user) {
        return notificationRepository.countByUserIdAndReadFalse(user.getId());
    }
}
