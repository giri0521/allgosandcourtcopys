package com.allgos.dms.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.allgos.dms.audit.entity.AuditAction;
import com.allgos.dms.audit.repository.AuditLogRepository;
import com.allgos.dms.auth.entity.RegistrationStatus;
import com.allgos.dms.auth.repository.OtpVerificationRepository;
import com.allgos.dms.auth.repository.RegistrationRequestRepository;
import com.allgos.dms.department.entity.Department;
import com.allgos.dms.department.repository.DepartmentRepository;
import com.allgos.dms.notification.entity.Notification;
import com.allgos.dms.notification.entity.NotificationType;
import com.allgos.dms.notification.repository.NotificationRepository;
import com.allgos.dms.support.AbstractIntegrationTest;
import com.allgos.dms.support.RecordingOtpProvider;
import com.allgos.dms.user.entity.User;
import com.allgos.dms.user.entity.UserStatus;
import com.allgos.dms.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The admin approval queue, end to end.
 *
 * <p>Two things are being proved here. First, that approval genuinely is the access gate: a member
 * cannot sign in until an admin approves, cannot reach {@code /admin/**} once they can, and loses
 * access the moment their account is disabled. Second, that every decision is recorded — an audit
 * row for the reviewer and a notification for the applicant, including the reason for a rejection.
 */
@Import(RecordingOtpProvider.Config.class)
class AdminApprovalIT extends AbstractIntegrationTest {

    private static final String ADMIN_MOBILE = "9999999999"; // seeded by V3
    private static final String MEMBER_MOBILE = "9876543211";
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
    private String adminToken;

    /**
     * Audit and notification rows are written outside the request transaction, so rollback cannot be
     * relied on for isolation. Everything belonging to the test member is removed explicitly, in
     * foreign-key order, leaving the seeded departments and first admin untouched.
     */
    @BeforeEach
    void setUp() throws Exception {
        otpProvider.clear();
        auditLogRepository.deleteAll();
        notificationRepository.deleteAll();
        registrationRequestRepository.deleteAll();
        otpVerificationRepository.deleteAll();
        userRepository.findByMobileNumber(MEMBER_MOBILE).ifPresent(userRepository::delete);

        // The seeded admin starts each test as it was seeded: active, and owing an OTP today.
        User admin = userRepository.findByMobileNumber(ADMIN_MOBILE).orElseThrow();
        admin.setStatus(UserStatus.ACTIVE);
        userRepository.saveAndFlush(admin);

        department = departmentRepository.findByActiveTrueOrderByNameAsc().getFirst();
        adminToken = signInWithOtp(ADMIN_MOBILE);
    }

    // ----------------------------------------------------------------- authorization

    @Test
    @DisplayName("a member is refused every admin endpoint")
    void memberCannotReachAdminEndpoints() throws Exception {
        register();
        approveOnly();
        String memberToken = signInWithOtp(MEMBER_MOBILE);
        UUID memberId = memberId();

        mockMvc.perform(get("/api/v1/admin/registration-requests").header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/admin/members").header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(patch("/api/v1/admin/members/{id}/status", memberId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "ACTIVE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("an anonymous caller is refused the admin endpoints")
    void anonymousCannotReachAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/registration-requests"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // --------------------------------------------------------------------- approval

    @Test
    @DisplayName("approving turns a pending registration into an account that can sign in")
    void approveActivatesTheAccount() throws Exception {
        register();

        // The queue shows the applicant, with the details the reviewer decides on.
        UUID requestId = pendingRequestId();
        mockMvc.perform(get("/api/v1/admin/registration-requests?status=pending")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].mobileNumber").value(MEMBER_MOBILE))
                .andExpect(jsonPath("$.items[0].departmentName").value(department.getName()))
                .andExpect(jsonPath("$.items[0].accountStatus").value("PENDING"));

        approve(requestId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.reviewedByName").value("System Administrator"));

        assertThat(userRepository.findByMobileNumber(MEMBER_MOBILE).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
        assertThat(registrationRequestRepository.countByStatus(RegistrationStatus.PENDING)).isZero();

        // The applicant is told, and the decision is on the record.
        assertThat(notificationsFor(memberId()))
                .extracting(Notification::getType)
                .contains(NotificationType.REGISTRATION_APPROVED);
        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> AuditAction.REGISTRATION_APPROVED.equals(entry.getAction()));

        // And the whole point: sign-in now works.
        sendOtp(MEMBER_MOBILE);
        verifyOtp(MEMBER_MOBILE, otpProvider.lastOtpFor(MEMBER_MOBILE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("a request cannot be reviewed twice")
    void doubleReviewIsRefused() throws Exception {
        register();
        UUID requestId = pendingRequestId();

        approve(requestId).andExpect(status().isOk());

        approve(requestId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_ALREADY_REVIEWED"));

        reject(requestId, "changed my mind")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_ALREADY_REVIEWED"));
    }

    // -------------------------------------------------------------------- rejection

    @Test
    @DisplayName("rejecting requires a reason, and the applicant is told what it was")
    void rejectionCarriesItsReason() throws Exception {
        register();
        UUID requestId = pendingRequestId();

        mockMvc.perform(post("/api/v1/admin/registration-requests/{id}/reject", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "  "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        reject(requestId, "Not a member of this office")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reviewNote").value("Not a member of this office"));

        assertThat(userRepository.findByMobileNumber(MEMBER_MOBILE).orElseThrow().getStatus())
                .isEqualTo(UserStatus.REJECTED);

        assertThat(notificationsFor(memberId()))
                .filteredOn(entry -> NotificationType.REGISTRATION_REJECTED.equals(entry.getType()))
                .singleElement()
                .extracting(Notification::getBody)
                .isEqualTo("Not a member of this office");

        // Rejected is refused at sign-in, by the server, whatever the client shows.
        sendOtp(MEMBER_MOBILE);
        verifyOtp(MEMBER_MOBILE, otpProvider.lastOtpFor(MEMBER_MOBILE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_REJECTED"));
    }

    // ---------------------------------------------------------------------- members

    @Test
    @DisplayName("disabling an account takes effect on the next request, and enabling restores it")
    void disableThenEnable() throws Exception {
        register();
        approveOnly();
        String memberToken = signInWithOtp(MEMBER_MOBILE);
        UUID memberId = memberId();

        changeStatus(memberId, "INACTIVE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        assertThat(notificationsFor(memberId))
                .extracting(Notification::getType)
                .contains(NotificationType.ACCOUNT_DISABLED);

        // That token authenticated a request a moment ago — as a member, so 403. Now it does not
        // authenticate at all: the filter re-reads the user row, so a disabled account stops working
        // immediately rather than lasting until its access token expires.
        mockMvc.perform(get("/api/v1/admin/members").header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        passwordLogin(MEMBER_MOBILE)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));

        changeStatus(memberId, "ACTIVE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(userRepository.findById(memberId).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("an admin cannot disable their own account")
    void adminCannotDisableSelf() throws Exception {
        UUID adminId = userRepository.findByMobileNumber(ADMIN_MOBILE).orElseThrow().getId();

        changeStatus(adminId, "INACTIVE")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CANNOT_MODIFY_SELF"));
    }

    @Test
    @DisplayName("a pending account cannot be activated from the members screen")
    void pendingAccountMustGoThroughTheQueue() throws Exception {
        register();

        changeStatus(memberId(), "ACTIVE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REGISTRATION_NOT_REVIEWED"));

        assertThat(userRepository.findByMobileNumber(MEMBER_MOBILE).orElseThrow().getStatus())
                .isEqualTo(UserStatus.PENDING);
    }

    @Test
    @DisplayName("REJECTED is not a status an admin can set directly")
    void statusIsLimitedToEnableAndDisable() throws Exception {
        register();
        approveOnly();

        changeStatus(memberId(), "REJECTED")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STATUS_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("the members list filters by status and searches by name and mobile")
    void membersListFiltersAndSearches() throws Exception {
        register();

        mockMvc.perform(get("/api/v1/admin/members?status=pending")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].mobileNumber").value(MEMBER_MOBILE));

        // The seeded admin is active; the applicant is not, so the tabs really are separate.
        mockMvc.perform(get("/api/v1/admin/members?status=active")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].mobileNumber").value(ADMIN_MOBILE));

        mockMvc.perform(get("/api/v1/admin/members?q=" + MEMBER_MOBILE.substring(4))
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));

        mockMvc.perform(get("/api/v1/admin/members?q=nobody-by-this-name")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));

        mockMvc.perform(get("/api/v1/admin/members/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(1))
                .andExpect(jsonPath("$.active").value(1))
                .andExpect(jsonPath("$.pendingRequests").value(1));
    }

    @Test
    @DisplayName("an unknown status filter is rejected rather than silently ignored")
    void unknownStatusFilterIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/members?status=banana")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STATUS_INVALID"));
    }

    // ------------------------------------------------------------------------ helpers

    private ResultActions approve(UUID requestId) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/registration-requests/{id}/approve", requestId)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)));
    }

    private ResultActions reject(UUID requestId, String reason) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/registration-requests/{id}/reject", requestId)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", reason))));
    }

    private ResultActions changeStatus(UUID userId, String status) throws Exception {
        return mockMvc.perform(patch("/api/v1/admin/members/{id}/status", userId)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", status))));
    }

    /** Approves through the real endpoint, for tests whose subject is what happens afterwards. */
    private void approveOnly() throws Exception {
        approve(pendingRequestId()).andExpect(status().isOk());
    }

    private void register() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Meena Rajan",
                                "mobileNumber", MEMBER_MOBILE,
                                "departmentId", department.getId().toString(),
                                "designation", "Section Officer",
                                "password", PASSWORD))))
                .andExpect(status().isOk());
    }

    /** A full OTP sign-in, exactly as a user performs it, returning the access token. */
    private String signInWithOtp(String mobile) throws Exception {
        sendOtp(mobile);
        String body = verifyOtp(mobile, otpProvider.lastOtpFor(mobile))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private void sendOtp(String mobile) throws Exception {
        mockMvc.perform(post("/api/v1/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mobileNumber", mobile))))
                .andExpect(status().isAccepted());
    }

    private ResultActions verifyOtp(String mobile, String otp) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/otp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("mobileNumber", mobile, "otp", otp))));
    }

    private ResultActions passwordLogin(String mobile) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("mobileNumber", mobile, "password", PASSWORD))));
    }

    private UUID pendingRequestId() throws Exception {
        String body = mockMvc.perform(get("/api/v1/admin/registration-requests?status=pending")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode items = objectMapper.readTree(body).get("items");
        assertThat(items.size()).isPositive();
        return UUID.fromString(items.get(0).get("id").asText());
    }

    private UUID memberId() {
        return userRepository.findByMobileNumber(MEMBER_MOBILE).orElseThrow().getId();
    }

    /** Queried by id rather than walking the lazy association, which has no session out here. */
    private List<Notification> notificationsFor(UUID userId) {
        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 50))
                .getContent();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
