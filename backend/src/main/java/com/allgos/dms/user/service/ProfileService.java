package com.allgos.dms.user.service;

import com.allgos.dms.audit.entity.AuditAction;
import com.allgos.dms.audit.service.AuditService;
import com.allgos.dms.auth.dto.AuthResponses.CurrentUser;
import com.allgos.dms.common.exception.ApiException;
import com.allgos.dms.user.dto.ProfileRequests;
import com.allgos.dms.user.entity.User;
import com.allgos.dms.user.repository.UserRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a user may change about their own account.
 *
 * <p>The boundary matters more than the code: a member may correct their name, email and
 * designation, and may change their password. They may not move themselves between departments,
 * grant themselves a role, or reactivate a disabled account — those are an administrator's
 * decisions, and none of them is reachable from here.
 *
 * <p>Editing a profile never returns an account to PENDING. Approval happens once; the only way an
 * approved account loses access is an admin setting it to INACTIVE.
 */
@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public ProfileService(
            UserRepository userRepository, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    /**
     * The signed-in user, re-read from the database.
     *
     * <p>Deliberately not taken from the principal: the row is authoritative, and an admin may have
     * changed something since the token was issued.
     */
    @Transactional(readOnly = true)
    public CurrentUser current(User user) {
        return CurrentUser.from(managed(user));
    }

    /** Records what actually changed, so the audit entry is worth reading. */
    @Transactional
    public CurrentUser update(User user, ProfileRequests.UpdateProfile request) {
        User managed = managed(user);

        Map<String, Object> changes = new LinkedHashMap<>();
        String fullName = request.fullName().trim();
        String email = blankToNull(request.email());
        String designation = blankToNull(request.designation());

        if (!fullName.equals(managed.getFullName())) {
            changes.put("fullName", fullName);
            managed.setFullName(fullName);
        }
        if (!java.util.Objects.equals(email, managed.getEmail())) {
            changes.put("email", email == null ? "" : email);
            managed.setEmail(email);
        }
        if (!java.util.Objects.equals(designation, managed.getDesignation())) {
            changes.put("designation", designation == null ? "" : designation);
            managed.setDesignation(designation);
        }

        // Nothing changed is not an error, but it is not worth an audit row either.
        if (!changes.isEmpty()) {
            auditService.record(managed, AuditAction.USER_UPDATED, "user", managed.getId(), changes);
        }

        return CurrentUser.from(managed);
    }

    /**
     * Changes the password of the signed-in user.
     *
     * <p>Every session is ended, here as in the OTP reset: the token version moves forward, so every
     * access and refresh token already issued — including the one making this request — stops being
     * accepted. If the reason for the change was that somebody else had the old password, leaving
     * their session alive would defeat the point.
     *
     * <p>The daily-OTP stamp is deliberately left alone. It records that this person proved
     * possession of their phone today, which changing a password neither confirms nor invalidates.
     */
    @Transactional
    public void changePassword(User user, ProfileRequests.ChangePassword request) {
        User managed = managed(user);

        if (managed.getPasswordHash() == null
                || !passwordEncoder.matches(request.currentPassword(), managed.getPasswordHash())) {
            // Durable: the exception below rolls this transaction back, and a rejected attempt on a
            // signed-in account is exactly the event a security review needs to see.
            auditService.recordDurable(
                    managed, AuditAction.LOGIN_FAILED, "user", managed.getId(),
                    Map.of("reason", "wrong_current_password"));
            throw ApiException.badRequest("CURRENT_PASSWORD_INVALID", "That is not your current password.");
        }

        if (passwordEncoder.matches(request.newPassword(), managed.getPasswordHash())) {
            throw ApiException.badRequest(
                    "PASSWORD_UNCHANGED", "Choose a password different from your current one.");
        }

        managed.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        managed.setFailedLoginCount(0);
        managed.setLockedUntil(null);
        managed.setTokenVersion(managed.getTokenVersion() + 1);

        auditService.record(managed, AuditAction.PASSWORD_CHANGED, "user", managed.getId(), null);
    }

    /**
     * The principal was loaded by the authentication filter and is detached by now, so it is read
     * again inside this transaction before anything is written through it.
     */
    private User managed(User user) {
        return userRepository.findById(user.getId()).orElseThrow(() -> ApiException.notFound("User"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
