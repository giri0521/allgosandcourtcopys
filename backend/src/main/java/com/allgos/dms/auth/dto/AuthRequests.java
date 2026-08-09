package com.allgos.dms.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Request bodies for the authentication endpoints. */
public final class AuthRequests {

    /** Indian mobile numbers: 10 digits, first digit 6-9, no country code. */
    private static final String MOBILE_PATTERN = "^[6-9]\\d{9}$";

    private static final String MOBILE_MESSAGE = "Enter a valid 10-digit mobile number";

    public record Register(
            @NotBlank(message = "Full name is required")
            @Size(max = 255)
            String fullName,

            @Pattern(regexp = MOBILE_PATTERN, message = MOBILE_MESSAGE)
            String mobileNumber,

            @NotNull(message = "Select your department")
            UUID departmentId,

            @Size(max = 255)
            String designation,

            @Email(message = "Enter a valid email address")
            String email,

            /**
             * Used from the second sign-in of each day onward; the first is always OTP. Required at
             * registration so that a member is never left without a way back in after their daily
             * OTP.
             */
            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 72, message = "Password must be at least 8 characters")
            String password) {}

    public record SendOtp(
            @Pattern(regexp = MOBILE_PATTERN, message = MOBILE_MESSAGE)
            String mobileNumber) {}

    public record VerifyOtp(
            @Pattern(regexp = MOBILE_PATTERN, message = MOBILE_MESSAGE)
            String mobileNumber,

            @NotBlank(message = "Enter the OTP")
            String otp) {}

    public record PasswordLogin(
            @Pattern(regexp = MOBILE_PATTERN, message = MOBILE_MESSAGE)
            String mobileNumber,

            @NotBlank(message = "Enter your password")
            String password) {}

    public record ResetPassword(
            @Pattern(regexp = MOBILE_PATTERN, message = MOBILE_MESSAGE)
            String mobileNumber,

            @NotBlank(message = "Enter the OTP")
            String otp,

            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 72, message = "Password must be at least 8 characters")
            String newPassword) {}

    private AuthRequests() {}
}
