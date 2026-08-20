package com.allgos.dms.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.allgos.dms.audit.repository.AuditLogRepository;
import com.allgos.dms.auth.repository.OtpVerificationRepository;
import com.allgos.dms.auth.repository.RegistrationRequestRepository;
import com.allgos.dms.department.entity.Department;
import com.allgos.dms.department.repository.DepartmentRepository;
import com.allgos.dms.notification.repository.NotificationRepository;
import com.allgos.dms.support.AbstractIntegrationTest;
import com.allgos.dms.support.RecordingOtpProvider;
import com.allgos.dms.user.entity.User;
import com.allgos.dms.user.entity.UserStatus;
import com.allgos.dms.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * End-to-end proof of the rules that gate the whole system: approval is required before any token is
 * issued, a session is started with a password, and the one OTP path that leads back into an
 * account — the forgotten-password reset — is bounded against guessing.
 */
@Import(RecordingOtpProvider.Config.class)
class AuthFlowIT extends AbstractIntegrationTest {

    private static final String MOBILE = "9876543210";
    private static final String PASSWORD = "Str0ngPassword!";
    private static final String NEW_PASSWORD = "N3wPassword!";

    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private RegistrationRequestRepository registrationRequestRepository;
    @Autowired private OtpVerificationRepository otpVerificationRepository;
    @Autowired private RecordingOtpProvider otpProvider;

    private Department department;

    /**
     * Audit rows are written in their own transaction so they survive rollbacks, which is exactly
     * why these tests cannot lean on transactional rollback for isolation. Everything referencing
     * the test user is cleared explicitly, in foreign-key order, leaving the seeded departments and
     * first admin in place.
     */
    @BeforeEach
    void setUp() {
        otpProvider.clear();
        auditLogRepository.deleteAll();
        notificationRepository.deleteAll();
        registrationRequestRepository.deleteAll();
        otpVerificationRepository.deleteAll();
        userRepository.findByMobileNumber(MOBILE).ifPresent(userRepository::delete);

        department = departmentRepository.findByActiveTrueOrderByNameAsc().getFirst();
    }

    @Test
    @DisplayName("the 43 departments are seeded by migration")
    void departmentsAreSeeded() {
        assertThat(departmentRepository.count()).isEqualTo(43);
    }

    @Test
    @DisplayName("a new registration lands as PENDING and cannot sign in")
    void registrationIsPendingAndCannotSignIn() throws Exception {
        register().andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        User created = userRepository.findByMobileNumber(MOBILE).orElseThrow();
        assertThat(created.getStatus()).isEqualTo(UserStatus.PENDING);

        // The password is correct; the account simply has not been approved — refused server-side.
        passwordLogin(PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_PENDING"));
    }

    @Test
    @DisplayName("after approval the registered password signs in")
    void passwordSignsInAfterApproval() throws Exception {
        register();
        approve();

        passwordLogin(PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.status").value("ACTIVE"));

        // And it keeps working: nothing about signing in is once-per-day.
        passwordLogin(PASSWORD).andExpect(status().isOk());

        assertThat(userRepository.findByMobileNumber(MOBILE).orElseThrow().getLastLoginAt())
                .isNotNull();
    }

    @Test
    @DisplayName("wrong passwords are refused and lock the account")
    void wrongPasswordsLockTheAccount() throws Exception {
        register();
        approve();

        // Each rejection rolls the request transaction back. The failure counter is committed
        // separately precisely so it is not lost with that rollback — without which password
        // guessing would be unbounded.
        for (int attempt = 1; attempt <= 5; attempt++) {
            passwordLogin("wrong-password")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }

        // Even the genuine password is now refused: the account is locked.
        passwordLogin(PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    @DisplayName("a forgotten password is reset over OTP, and the new one signs in")
    void passwordResetOverOtp() throws Exception {
        register();
        approve();

        sendPasswordResetOtp();
        resetPassword(otpProvider.lastOtpFor(MOBILE), NEW_PASSWORD)
                .andExpect(status().isNoContent());

        passwordLogin(NEW_PASSWORD).andExpect(status().isOk());
        passwordLogin(PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("a reset OTP is single-use, and a wrong one only costs an attempt")
    void resetOtpIsSingleUse() throws Exception {
        register();
        approve();
        sendPasswordResetOtp();

        resetPassword("000000", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_INVALID"));

        // The real code still works afterwards; only the attempt was consumed.
        resetPassword(otpProvider.lastOtpFor(MOBILE), NEW_PASSWORD)
                .andExpect(status().isNoContent());

        // Re-submitting the same code is refused: OTPs are single-use.
        resetPassword(otpProvider.lastOtpFor(MOBILE), "Another1Password!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_ALREADY_USED"));
    }

    @Test
    @DisplayName("reset OTP guesses are bounded, and the limit survives the rejection")
    void resetOtpAttemptsAreBounded() throws Exception {
        register();
        approve();
        sendPasswordResetOtp();

        for (int attempt = 1; attempt <= 5; attempt++) {
            resetPassword("000000", NEW_PASSWORD)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("OTP_INVALID"));
        }

        resetPassword("000000", NEW_PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("OTP_ATTEMPTS_EXCEEDED"));

        // Even the genuine code is now refused: the issued OTP is spent.
        resetPassword(otpProvider.lastOtpFor(MOBILE), NEW_PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("OTP_ATTEMPTS_EXCEEDED"));
    }

    @Test
    @DisplayName("registering a mobile number twice is refused")
    void duplicateMobileIsRejected() throws Exception {
        register().andExpect(status().isOk());
        register()
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MOBILE_ALREADY_REGISTERED"));
    }

    // ------------------------------------------------------------------------ helpers

    private ResultActions register() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fullName", "Test Member",
                        "mobileNumber", MOBILE,
                        "departmentId", department.getId().toString(),
                        "designation", "Section Officer",
                        "password", PASSWORD))));
    }

    /**
     * Approves directly, keeping these tests about the sign-in rules alone. The admin endpoint that
     * performs the same transition in production has its own coverage in {@code AdminApprovalIT}.
     */
    private void approve() {
        User user = userRepository.findByMobileNumber(MOBILE).orElseThrow();
        user.setStatus(UserStatus.ACTIVE);
        userRepository.saveAndFlush(user);
    }

    private void sendPasswordResetOtp() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mobileNumber", MOBILE))))
                .andExpect(status().isAccepted());
    }

    private ResultActions resetPassword(String otp, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "mobileNumber", MOBILE, "otp", otp, "newPassword", newPassword))));
    }

    private ResultActions passwordLogin(String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("mobileNumber", MOBILE, "password", password))));
    }
}
