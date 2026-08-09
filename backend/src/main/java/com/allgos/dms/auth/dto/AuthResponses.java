package com.allgos.dms.auth.dto;

import com.allgos.dms.user.entity.User;
import com.allgos.dms.user.entity.UserRole;
import com.allgos.dms.user.entity.UserStatus;
import java.time.Instant;
import java.util.UUID;

public final class AuthResponses {

    /** Returned on a successful sign-in. The refresh token is set as an httpOnly cookie, not here. */
    public record Session(String accessToken, long expiresInSeconds, CurrentUser user) {}

    /**
     * A sign-in result as it leaves the service layer: the response body plus the refresh token the
     * controller turns into a cookie. Keeping them together means the controller never has to
     * re-derive a token for a user it does not hold.
     */
    public record IssuedSession(Session session, String refreshToken) {}

    public record CurrentUser(
            UUID id,
            String fullName,
            String mobileNumber,
            String email,
            UserRole role,
            UserStatus status,
            UUID departmentId,
            String departmentName,
            String designation,
            Instant lastLoginAt,
            Instant createdAt) {

        public static CurrentUser from(User user) {
            return new CurrentUser(
                    user.getId(),
                    user.getFullName(),
                    user.getMobileNumber(),
                    user.getEmail(),
                    user.getRole(),
                    user.getStatus(),
                    user.getDepartment() == null ? null : user.getDepartment().getId(),
                    user.getDepartment() == null ? null : user.getDepartment().getName(),
                    user.getDesignation(),
                    user.getLastLoginAt(),
                    user.getCreatedAt());
        }
    }

    /**
     * The response to registration. Carries the status so the web app can route straight to the
     * Pending Approval screen.
     */
    public record Registered(UUID userId, UserStatus status, String message) {}

    private AuthResponses() {}
}
