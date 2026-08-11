package com.allgos.dms.admin.dto;

import com.allgos.dms.auth.entity.RegistrationRequest;
import com.allgos.dms.auth.entity.RegistrationStatus;
import com.allgos.dms.user.entity.User;
import com.allgos.dms.user.entity.UserRole;
import com.allgos.dms.user.entity.UserStatus;
import java.time.Instant;
import java.util.UUID;

/** Response bodies for the admin review endpoints. */
public final class AdminResponses {

    /**
     * A row in the approval queue, flattened.
     *
     * <p>The applicant's details are copied in rather than nested, because the reviewer decides from
     * exactly these fields and the queue should not need a second call per row.
     */
    public record RegistrationRequestView(
            UUID id,
            UUID userId,
            String fullName,
            String mobileNumber,
            String email,
            String designation,
            UUID departmentId,
            String departmentName,
            UserRole requestedRole,
            RegistrationStatus status,
            UserStatus accountStatus,
            String reviewNote,
            String reviewedByName,
            Instant reviewedAt,
            Instant submittedAt) {

        /** Must be called inside the transaction: department and reviewer are lazy associations. */
        public static RegistrationRequestView from(RegistrationRequest request) {
            User applicant = request.getUser();
            return new RegistrationRequestView(
                    request.getId(),
                    applicant.getId(),
                    applicant.getFullName(),
                    applicant.getMobileNumber(),
                    applicant.getEmail(),
                    applicant.getDesignation(),
                    applicant.getDepartment() == null ? null : applicant.getDepartment().getId(),
                    applicant.getDepartment() == null ? null : applicant.getDepartment().getName(),
                    request.getRequestedRole(),
                    request.getStatus(),
                    applicant.getStatus(),
                    request.getReviewNote(),
                    request.getReviewedBy() == null ? null : request.getReviewedBy().getFullName(),
                    request.getReviewedAt(),
                    request.getCreatedAt());
        }
    }

    /** A row in the members list. */
    public record MemberView(
            UUID id,
            String fullName,
            String mobileNumber,
            String email,
            String designation,
            UUID departmentId,
            String departmentName,
            UserRole role,
            UserStatus status,
            Instant lastLoginAt,
            Instant createdAt) {

        public static MemberView from(User user) {
            return new MemberView(
                    user.getId(),
                    user.getFullName(),
                    user.getMobileNumber(),
                    user.getEmail(),
                    user.getDesignation(),
                    user.getDepartment() == null ? null : user.getDepartment().getId(),
                    user.getDepartment() == null ? null : user.getDepartment().getName(),
                    user.getRole(),
                    user.getStatus(),
                    user.getLastLoginAt(),
                    user.getCreatedAt());
        }
    }

    /** Drives the members tabs and the "Pending Requests" tile, in one call instead of five. */
    public record MemberCounts(
            long all, long pending, long active, long inactive, long rejected, long pendingRequests) {}

    private AdminResponses() {}
}
