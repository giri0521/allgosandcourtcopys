package com.allgos.dms.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.allgos.dms.audit.entity.AuditAction;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The monitoring surface: a user's own profile, and the admin's view of everyone else.
 *
 * <p>What is being proved:
 *
 * <ol>
 *   <li>a member can correct their own details but cannot reach anybody else's, and cannot change
 *       their role, status or department through the profile endpoint;
 *   <li>changing a password ends every session, including the one that changed it;
 *   <li>the audit viewer filters, and a member is refused all of it;
 *   <li>the CSV export is a real spreadsheet — byte-order mark, quoted fields, an attachment name.
 * </ol>
 *
 * <p>No object storage here: nothing on these screens touches a document's bytes, so this extends
 * the plain database base class and skips starting MinIO.
 */
@Import(RecordingOtpProvider.Config.class)
class AdminMonitoringIT extends AbstractIntegrationTest {

    private static final String ADMIN_MOBILE = "9999999999"; // seeded by V3
    private static final String MEMBER_MOBILE = "9876543216";
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
    private String memberToken;
    private UUID memberId;

    @BeforeEach
    void setUp() throws Exception {
        otpProvider.clear();
        auditLogRepository.deleteAll();
        notificationRepository.deleteAll();
        registrationRequestRepository.deleteAll();
        otpVerificationRepository.deleteAll();
        userRepository.findByMobileNumber(MEMBER_MOBILE).ifPresent(userRepository::delete);

        User admin = userRepository.findByMobileNumber(ADMIN_MOBILE).orElseThrow();
        admin.setStatus(UserStatus.ACTIVE);
        userRepository.saveAndFlush(admin);

        department = departmentRepository.findByActiveTrueOrderByNameAsc().getFirst();

        adminToken = signInWithOtp(ADMIN_MOBILE);
        memberToken = registerApproveAndSignIn(MEMBER_MOBILE, "Meena Rajan");
        memberId = userRepository.findByMobileNumber(MEMBER_MOBILE).orElseThrow().getId();
    }

    // ------------------------------------------------------------------------ profile

    @Test
    @DisplayName("a member reads and corrects their own profile")
    void memberEditsTheirOwnProfile() throws Exception {
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Meena Rajan"))
                .andExpect(jsonPath("$.mobileNumber").value(MEMBER_MOBILE))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(patch("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Meena Rajan",
                                "email", "meena@example.gov.in",
                                "designation", "Superintendent"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designation").value("Superintendent"))
                .andExpect(jsonPath("$.email").value("meena@example.gov.in"));

        User updated = userRepository.findById(memberId).orElseThrow();
        assertThat(updated.getDesignation()).isEqualTo("Superintendent");
        // The things a profile edit must never touch.
        assertThat(updated.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(updated.getDepartment().getId()).isEqualTo(department.getId());
        assertThat(updated.isAdmin()).isFalse();

        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> AuditAction.USER_UPDATED.equals(entry.getAction()));
    }

    @Test
    @DisplayName("a profile edit cannot smuggle in a role, a status or a department")
    void extraFieldsAreIgnored() throws Exception {
        mockMvc.perform(patch("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"fullName":"Meena Rajan","role":"ADMIN","status":"INACTIVE",
                                 "departmentId":"00000000-0000-0000-0000-000000000000"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"));

        User untouched = userRepository.findById(memberId).orElseThrow();
        assertThat(untouched.isAdmin()).isFalse();
        assertThat(untouched.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(untouched.getDepartment().getId()).isEqualTo(department.getId());
    }

    @Test
    @DisplayName("changing a password ends every session, including the one that changed it")
    void changingAPasswordSignsEveryoneOut() throws Exception {
        mockMvc.perform(post("/api/v1/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("currentPassword", PASSWORD, "newPassword", "N3wPassword!"))))
                .andExpect(status().isNoContent());

        // The token that made the request is no longer accepted.
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isUnauthorized());

        // The new password works; the old one does not. An OTP already succeeded today, so the
        // password path is open — see the daily-OTP rule.
        passwordLogin(MEMBER_MOBILE, "N3wPassword!").andExpect(status().isOk());
        passwordLogin(MEMBER_MOBILE, PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("a wrong current password is refused, and the rejection survives the rollback")
    void wrongCurrentPasswordIsRecorded() throws Exception {
        mockMvc.perform(post("/api/v1/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("currentPassword", "wrong", "newPassword", "N3wPassword!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));

        // The session is untouched, and the attempt is on the record.
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isOk());
        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> AuditAction.LOGIN_FAILED.equals(entry.getAction()));
    }

    @Test
    @DisplayName("/me needs a session")
    void profileRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- admin monitoring

    @Test
    @DisplayName("a member is refused every monitoring endpoint")
    void memberCannotReachMonitoring() throws Exception {
        for (String path : List.of(
                "/api/v1/admin/stats",
                "/api/v1/admin/activity",
                "/api/v1/admin/audit-logs",
                "/api/v1/admin/audit-logs/actions",
                "/api/v1/admin/reports",
                "/api/v1/admin/reports/export")) {

            mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        mockMvc.perform(get("/api/v1/admin/members/{id}/activity", memberId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the dashboard counts what exists")
    void statsCountTheSystem() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departments").value(43))
                .andExpect(jsonPath("$.documents").value(0))
                .andExpect(jsonPath("$.pendingRequests").value(0))
                // The seeded admin plus the member registered in setUp.
                .andExpect(jsonPath("$.members").value(2))
                .andExpect(jsonPath("$.activeMembers").value(2));
    }

    @Test
    @DisplayName("a member's activity shows their own trail and nobody else's")
    void memberActivityIsScopedToThatMember() throws Exception {
        String body = mockMvc.perform(get("/api/v1/admin/members/{id}/activity", memberId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.member.fullName").value("Meena Rajan"))
                .andExpect(jsonPath("$.summary.uploads").value(0))
                .andExpect(jsonPath("$.summary.logins").value(1))
                .andExpect(jsonPath("$.timeline.totalItems").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Every entry belongs to this member — the trail is filtered, not merely sorted.
        var timeline = objectMapper.readTree(body).get("timeline").get("items");
        assertThat(timeline).isNotEmpty();
        timeline.forEach(entry ->
                assertThat(entry.get("actorId").asText()).isEqualTo(memberId.toString()));
    }

    @Test
    @DisplayName("an unknown member is a 404 rather than an empty timeline")
    void unknownMemberIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/admin/members/{id}/activity", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ audit viewer

    @Test
    @DisplayName("the audit viewer filters by actor, by action and by date")
    void auditLogFiltersNarrowTheTrail() throws Exception {
        long total = auditLogRepository.count();
        assertThat(total).isPositive();

        mockMvc.perform(get("/api/v1/admin/audit-logs").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value((int) total));

        // By actor.
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("actorId", memberId.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].actorName").value("Meena Rajan"));

        // By action — registration happened exactly once in setUp.
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("action", AuditAction.REGISTER)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].action").value(AuditAction.REGISTER));

        // Today includes everything just written; a window that ended yesterday includes none of it.
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("from", LocalDate.now().toString())
                        .param("to", LocalDate.now().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(jsonPath("$.totalItems").value((int) total));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("to", LocalDate.now().minusDays(1).toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    @DisplayName("the filter's action list comes from the constants, so it cannot drift")
    void auditActionsAreOffered() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs/actions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasItem(AuditAction.FILE_DELETED)))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasItem(AuditAction.REGISTRATION_APPROVED)));
    }

    // ---------------------------------------------------------------------- reports

    @Test
    @DisplayName("the report lists every department, including the quiet ones")
    void reportCoversEveryDepartment() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departments.length()").value(43))
                .andExpect(jsonPath("$.monthly.length()").value(12))
                .andExpect(jsonPath("$.topUploaders").isArray());
    }

    @Test
    @DisplayName("a backwards period is refused")
    void reportRejectsABackwardsPeriod() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports")
                        .param("from", "2026-03-01")
                        .param("to", "2026-01-01")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RANGE_INVALID"));
    }

    @Test
    @DisplayName("the export is a spreadsheet Excel will open correctly")
    void exportIsARealCsv() throws Exception {
        byte[] csv = mockMvc.perform(get("/api/v1/admin/reports/export")
                        .param("type", "departments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("attachment")))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        // The byte-order mark, without which Tamil department names arrive as mojibake.
        assertThat(csv[0]).isEqualTo((byte) 0xEF);
        assertThat(csv[1]).isEqualTo((byte) 0xBB);
        assertThat(csv[2]).isEqualTo((byte) 0xBF);

        String text = new String(csv, StandardCharsets.UTF_8);
        assertThat(text).startsWith("﻿\"Department\",\"Documents held\",\"Uploads\",\"Downloads\"\r\n");
        // One line per department plus the header.
        assertThat(text.lines().count()).isEqualTo(44);
    }

    @Test
    @DisplayName("an unknown report type is refused rather than exporting an empty file")
    void unknownExportIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/export")
                        .param("type", "banana")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPORT_UNKNOWN"));
    }

    // ------------------------------------------------------------------------ helpers

    private ResultActions passwordLogin(String mobile, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("mobileNumber", mobile, "password", password))));
    }

    private String registerApproveAndSignIn(String mobile, String name) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", name,
                                "mobileNumber", mobile,
                                "departmentId", department.getId().toString(),
                                "designation", "Section Officer",
                                "password", PASSWORD))))
                .andExpect(status().isOk());

        UUID applicantId = userRepository.findByMobileNumber(mobile).orElseThrow().getId();
        UUID requestId = registrationRequestRepository
                .findFirstByUserIdOrderByCreatedAtDesc(applicantId)
                .orElseThrow()
                .getId();

        mockMvc.perform(post("/api/v1/admin/registration-requests/{id}/approve", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());

        return signInWithOtp(mobile);
    }

    private String signInWithOtp(String mobile) throws Exception {
        mockMvc.perform(post("/api/v1/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mobileNumber", mobile))))
                .andExpect(status().isAccepted());

        String body = mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("mobileNumber", mobile, "otp", otpProvider.lastOtpFor(mobile)))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
