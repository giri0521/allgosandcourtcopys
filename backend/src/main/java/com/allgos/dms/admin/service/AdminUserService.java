package com.allgos.dms.admin.service;

import com.allgos.dms.admin.dto.AdminResponses.MemberCounts;
import com.allgos.dms.admin.dto.AdminResponses.MemberView;
import com.allgos.dms.admin.dto.AdminResponses.RegistrationRequestView;
import com.allgos.dms.audit.entity.AuditAction;
import com.allgos.dms.audit.service.AuditService;
import com.allgos.dms.auth.entity.RegistrationRequest;
import com.allgos.dms.auth.entity.RegistrationStatus;
import com.allgos.dms.auth.repository.RegistrationRequestRepository;
import com.allgos.dms.common.dto.PageResponse;
import com.allgos.dms.common.exception.ApiException;
import com.allgos.dms.notification.service.NotificationService;
import com.allgos.dms.user.entity.User;
import com.allgos.dms.user.entity.UserRole;
import com.allgos.dms.user.entity.UserStatus;
import com.allgos.dms.user.repository.UserRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The approval gate, from the administrator's side.
 *
 * <p>Admin approval is the only access control in the system, so every transition here is deliberate
 * and audited: an account moves out of PENDING exactly once, by an admin, and the applicant is told
 * what happened. Nothing in this class is reachable without the ADMIN role — see
 * {@code AdminUserController}, which carries the {@code @PreAuthorize} for the whole surface.
 */
@Service
public class AdminUserService {

    private final RegistrationRequestRepository registrationRequestRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public AdminUserService(
            RegistrationRequestRepository registrationRequestRepository,
            UserRepository userRepository,
            AuditService auditService,
            NotificationService notificationService) {
        this.registrationRequestRepository = registrationRequestRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    // ------------------------------------------------------------- registration queue

    /** @param status null lists every request, whatever its outcome */
    @Transactional(readOnly = true)
    public PageResponse<RegistrationRequestView> listRegistrationRequests(
            RegistrationStatus status, Pageable pageable) {

        Page<RegistrationRequest> page = status == null
                ? registrationRequestRepository.findAll(pageable)
                : registrationRequestRepository.findByStatus(status, pageable);

        return PageResponse.of(page, RegistrationRequestView::from);
    }

    @Transactional(readOnly = true)
    public RegistrationRequestView getRegistrationRequest(UUID requestId) {
        return RegistrationRequestView.from(findRequest(requestId));
    }

    /**
     * Approves a registration: the account becomes ACTIVE and, from this moment, can sign in with
     * the password chosen at registration.
     *
     * <p>The applicant's role is left exactly as registered. Promotion to admin is a separate,
     * deliberate act, so an approval click can never hand out administrative access by accident.
     */
    @Transactional
    public RegistrationRequestView approve(UUID requestId, User admin) {
        User reviewer = managed(admin);
        RegistrationRequest request = findPendingRequest(requestId);
        User applicant = request.getUser();

        applicant.setStatus(UserStatus.ACTIVE);
        markReviewed(request, RegistrationStatus.APPROVED, reviewer, null);

        auditService.record(reviewer, AuditAction.REGISTRATION_APPROVED, "user", applicant.getId(),
                Map.of("requestId", request.getId().toString()));
        notificationService.notifyRegistrationApproved(applicant);

        return RegistrationRequestView.from(request);
    }

    /**
     * Rejects a registration. REJECTED is terminal: the applicant registers again rather than being
     * revived, which keeps a rejected account from quietly becoming active later.
     */
    @Transactional
    public RegistrationRequestView reject(UUID requestId, String reason, User admin) {
        User reviewer = managed(admin);
        RegistrationRequest request = findPendingRequest(requestId);
        User applicant = request.getUser();

        applicant.setStatus(UserStatus.REJECTED);
        markReviewed(request, RegistrationStatus.REJECTED, reviewer, reason.trim());

        auditService.record(reviewer, AuditAction.REGISTRATION_REJECTED, "user", applicant.getId(),
                Map.of("requestId", request.getId().toString(), "reason", reason.trim()));
        notificationService.notifyRegistrationRejected(applicant, reason.trim());

        return RegistrationRequestView.from(request);
    }

    // ---------------------------------------------------------------------- members

    /**
     * @param status null returns every account, which is the "All" tab
     * @param query matched against name and mobile number; blank means no filter
     */
    @Transactional(readOnly = true)
    public PageResponse<MemberView> listMembers(UserStatus status, String query, Pageable pageable) {
        String term = query == null || query.isBlank()
                ? "%"
                : "%" + query.trim().toLowerCase() + "%";

        Page<User> page = status == null
                ? userRepository.search(term, pageable)
                : userRepository.searchByStatus(status, term, pageable);

        return PageResponse.of(page, MemberView::from);
    }

    @Transactional(readOnly = true)
    public MemberCounts counts() {
        return new MemberCounts(
                userRepository.count(),
                userRepository.countByStatus(UserStatus.PENDING),
                userRepository.countByStatus(UserStatus.ACTIVE),
                userRepository.countByStatus(UserStatus.INACTIVE),
                userRepository.countByStatus(UserStatus.REJECTED),
                registrationRequestRepository.countByStatus(RegistrationStatus.PENDING));
    }

    /**
     * Enables or disables an account that has already been through review.
     *
     * <p>Two guards matter here. An admin cannot change their own status, which is the cheapest way
     * to lock the last administrator out of the system. And a PENDING or REJECTED account cannot be
     * switched on from this screen: it must go through the approval queue, so that every activation
     * leaves a reviewed request behind it.
     *
     * <p>Disabling takes effect on the caller's very next request, because
     * {@code JwtAuthenticationFilter} re-reads the user row instead of trusting the token.
     */
    @Transactional
    public MemberView changeStatus(UUID userId, UserStatus target, User admin) {
        if (target != UserStatus.ACTIVE && target != UserStatus.INACTIVE) {
            throw ApiException.badRequest(
                    "STATUS_NOT_ALLOWED", "An account can only be enabled or disabled here");
        }
        if (userId.equals(admin.getId())) {
            throw ApiException.forbidden(
                    "CANNOT_MODIFY_SELF", "You cannot change the status of your own account");
        }

        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> ApiException.notFound("Member"));

        if (user.getStatus() == UserStatus.PENDING || user.getStatus() == UserStatus.REJECTED) {
            throw ApiException.conflict(
                    "REGISTRATION_NOT_REVIEWED",
                    "Approve this registration from the requests queue first");
        }

        UserStatus previous = user.getStatus();
        if (previous == target) {
            return MemberView.from(user);
        }

        user.setStatus(target);
        auditService.record(managed(admin), AuditAction.USER_STATUS_CHANGED, "user", user.getId(),
                Map.of("from", previous.name(), "to", target.name()));

        if (target == UserStatus.INACTIVE) {
            notificationService.notifyAccountDisabled(user);
        }

        return MemberView.from(user);
    }

    /**
     * Grants or withdraws administrative access.
     *
     * <p>This is the only way a second admin is ever made. Registration hardcodes MEMBER and
     * approval leaves the role alone, precisely so that nobody can arrive at the role by signing up
     * — it has to be handed over deliberately, by somebody who already holds it, and the audit row
     * says who did it.
     *
     * <p>An admin cannot change their own role. That blocks the only self-inflicted lockout
     * available here — demoting yourself when you are the last admin, leaving an installation with
     * no way back in short of editing the database by hand. It also means the caller is always an
     * admin other than the target, so a demotion can never remove the last one.
     *
     * <p>The role travels inside the JWT, so a change that only touched the column would leave the
     * old role live in the target's current token for as long as it lasts. Incrementing
     * {@code tokenVersion} retires every token they hold: withdrawn access stops now rather than up
     * to thirty minutes from now, and a promotion is picked up on their next sign-in rather than
     * appearing to do nothing.
     */
    @Transactional
    public MemberView changeRole(UUID userId, UserRole target, User admin) {
        if (userId.equals(admin.getId())) {
            throw ApiException.forbidden(
                    "CANNOT_MODIFY_SELF", "You cannot change your own role");
        }

        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> ApiException.notFound("Member"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.conflict(
                    "MEMBER_NOT_ACTIVE",
                    "Only an active member can be made an administrator");
        }

        UserRole previous = user.getRole();
        if (previous == target) {
            return MemberView.from(user);
        }

        user.setRole(target);
        user.setTokenVersion(user.getTokenVersion() + 1);

        auditService.record(managed(admin), AuditAction.USER_ROLE_CHANGED, "user", user.getId(),
                Map.of("from", previous.name(), "to", target.name()));
        notificationService.notifyRoleChanged(user, target);

        return MemberView.from(user);
    }

    // ------------------------------------------------------------------------ helpers

    /**
     * Re-reads the caller inside this transaction.
     *
     * <p>The principal was loaded by the authentication filter and is detached by the time it
     * arrives here, so attaching it to a reviewed request or an audit row would leave Hibernate to
     * guess. Reading it again is one indexed lookup and removes the question entirely.
     */
    private User managed(User admin) {
        return userRepository.findById(admin.getId()).orElseThrow(() -> ApiException.notFound("User"));
    }

    private void markReviewed(
            RegistrationRequest request, RegistrationStatus outcome, User admin, String note) {
        request.setStatus(outcome);
        request.setReviewedBy(admin);
        request.setReviewedAt(Instant.now());
        request.setReviewNote(note);
    }

    private RegistrationRequest findRequest(UUID requestId) {
        return registrationRequestRepository
                .findById(requestId)
                .orElseThrow(() -> ApiException.notFound("Registration request"));
    }

    /**
     * Reviewing twice is refused rather than silently repeated, so two admins working the queue at
     * the same time cannot have the second click overwrite the first one's decision.
     */
    private RegistrationRequest findPendingRequest(UUID requestId) {
        RegistrationRequest request = findRequest(requestId);
        if (request.getStatus() != RegistrationStatus.PENDING) {
            throw ApiException.conflict(
                    "REQUEST_ALREADY_REVIEWED",
                    "This request was already " + request.getStatus().name().toLowerCase());
        }
        return request;
    }
}
