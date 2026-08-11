package com.allgos.dms.admin.dto;

import com.allgos.dms.user.entity.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request bodies for the admin review endpoints. */
public final class AdminRequests {

    /**
     * Rejecting always carries a reason, because the applicant is told why — and because a rejection
     * with no explanation is the fastest way to generate a phone call to the office.
     */
    public record Reject(
            @NotBlank(message = "Give a reason for the rejection")
            @Size(max = 500, message = "Keep the reason under 500 characters")
            String reason) {}

    /** Enable or disable an already-reviewed account. Only ACTIVE and INACTIVE are accepted. */
    public record ChangeStatus(
            @NotNull(message = "Choose a status")
            UserStatus status) {}

    private AdminRequests() {}
}
