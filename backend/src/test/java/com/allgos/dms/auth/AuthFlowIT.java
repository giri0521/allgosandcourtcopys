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
 * End-to-end proof of the two rules that gate the whole system:
 * approval is required before any token is issued, and the first sign-in of each day must be an OTP.
 */
@Import(RecordingOtpProvider.Config.class)
class AuthFlowIT extends AbstractIntegrationTest {

    private static final String MOBILE = "9876543210";
    private static final String PASSWORD = "Str0ngPassword!";

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
        assertThat(created.getLastOtpLoginDate()).isNull();

        // Even with a correct OTP, an unapproved account is refused — server-side.
        sendLoginOtp();
        verifyLoginOtp(otpProvider.lastOtpFor(MOBILE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_PENDING"));

        // And so is the password path.
        passwordLogin(PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_PENDING"));
    }

    @Test
    @DisplayName("after approval: OTP first, then password for the rest of the day")
    void dailyOtpThenPassword() throws Exception {
        register();
        approve();

        // 1. Password alone is refused on the first attempt of the day.
        passwordLogin(PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("OTP_REQUIRED_TODAY"));

        // 2. OTP works and starts a session.
        sendLoginOtp();
        verifyLoginOtp(otpProvider.lastOtpFor(MOBILE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.status").value("ACTIVE"));

        assertThat(userRepository.findByMobileNumber(MOBILE).orElseThrow().getLastOtpLoginDate())
                .isNotNull();

        // 3. Password now works for the remainder of the day.
        passwordLogin(PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("the OTP requirement returns the next day")
    void otpIsRequiredAgainTheNextDay() throws Exception {
        register();
        approve();
        sendLoginOtp();
        verifyLoginOtp(otpProvider.lastOtpFor(MOBILE)).andExpect(status().isOk());
        passwordLogin(PASSWORD).andExpect(status().isOk());

        // Simulate the date rolling over by ageing the stamp, which is exactly what midnight does.
        User user = userRepository.findByMobileNumber(MOBILE).orElseThrow();
        user.setLastOtpLoginDate(user.getLastOtpLoginDate().minusDays(1));
        userRepository.saveAndFlush(user);

        passwordLogin(PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("OTP_REQUIRED_TODAY"));
    }

    @Test
    @DisplayName("a wrong OTP is rejected and cannot be replayed")
    void wrongOtpIsRejected() throws Exception {
        register();
        approve();
        sendLoginOtp();

        verifyLoginOtp("000000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_INVALID"));

        // The real code still works afterwards; only the attempt was consumed.
        verifyLoginOtp(otpProvider.lastOtpFor(MOBILE)).andExpect(status().isOk());

        // Re-submitting the same code is refused: OTPs are single-use.
        verifyLoginOtp(otpProvider.lastOtpFor(MOBILE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_ALREADY_USED"));
    }

    @Test
    @DisplayName("OTP guesses are bounded, and the limit survives the rejection")
    void otpAttemptsAreBounded() throws Exception {
        register();
        approve();
        sendLoginOtp();

        // Each rejection throws, rolling back the request transaction. The attempt counter is
        // committed separately precisely so it is not lost with that rollback — without which
        // guessing a 6-digit code would be unlimited.
        for (int attempt = 1; attempt <= 5; attempt++) {
            verifyLoginOtp("000000")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("OTP_INVALID"));
        }

        verifyLoginOtp("000000")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("OTP_ATTEMPTS_EXCEEDED"));

        // Even the genuine code is now refused: the issued OTP is spent.
        verifyLoginOtp(otpProvider.lastOtpFor(MOBILE))
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

    /** Stands in for the admin approval endpoint, which arrives in Phase 2. */
    private void approve() {
        User user = userRepository.findByMobileNumber(MOBILE).orElseThrow();
        user.setStatus(UserStatus.ACTIVE);
        userRepository.saveAndFlush(user);
    }

    private void sendLoginOtp() throws Exception {
        mockMvc.perform(post("/api/v1/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mobileNumber", MOBILE))))
                .andExpect(status().isAccepted());
    }

    private ResultActions verifyLoginOtp(String otp) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/otp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("mobileNumber", MOBILE, "otp", otp))));
    }

    private ResultActions passwordLogin(String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("mobileNumber", MOBILE, "password", password))));
    }
}
