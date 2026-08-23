package com.allgos.dms.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request bodies for the endpoints a user points at their own account. */
public final class ProfileRequests {

    /**
     * The three fields a member may correct about themselves.
     *
     * <p>Notably absent: mobile number, department, role and status. The mobile number is the login
     * identity and changing it is an account transfer, not an edit; the rest are an administrator's
     * decision. A profile edit never returns an account to PENDING — approval happens once.
     */
    public record UpdateProfile(
            @NotBlank(message = "Full name is required")
            @Size(max = 255)
            String fullName,

            @Email(message = "Enter a valid email address")
            @Size(max = 255)
            String email,

            @Size(max = 255)
            String designation,

            /** Fills the From block on every letter, and is editable there per letter. */
            @Size(max = 500, message = "Keep the address under 500 characters")
            String officeAddress) {}

    /**
     * Changing a password while signed in.
     *
     * <p>The current password is required even though the caller is authenticated: an unattended
     * session is the ordinary way an account is taken over, and asking for it is what stops a
     * passer-by locking out the real owner. Someone who has forgotten it uses the OTP reset instead.
     */
    public record ChangePassword(
            @NotBlank(message = "Enter your current password")
            String currentPassword,

            @NotBlank(message = "Enter a new password")
            @Size(min = 8, max = 72, message = "Password must be at least 8 characters")
            String newPassword) {}

    private ProfileRequests() {}
}
