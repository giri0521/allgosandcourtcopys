package com.allgos.dms.notification.service;

import com.allgos.dms.notification.entity.Notification;
import com.allgos.dms.notification.entity.NotificationType;
import com.allgos.dms.notification.repository.NotificationRepository;
import com.allgos.dms.user.entity.User;
import com.allgos.dms.user.entity.UserRole;
import com.allgos.dms.user.entity.UserStatus;
import com.allgos.dms.user.repository.UserRepository;
import java.util.List;
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
        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setEntityRef(entityRef);
        notificationRepository.save(notification);
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
                "You can now sign in. The first sign-in of each day is verified with an OTP.",
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

    @Transactional
    public void markRead(User user, java.util.UUID notificationId) {
        notificationRepository
                .findById(notificationId)
                .filter(notification -> notification.getUser().getId().equals(user.getId()))
                .ifPresent(notification -> notification.setRead(true));
    }

    public long unreadCount(User user) {
        return notificationRepository.countByUserIdAndReadFalse(user.getId());
    }
}
