package com.allgos.dms.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.allgos.dms.audit.entity.AuditAction;
import com.allgos.dms.audit.repository.AuditLogRepository;
import com.allgos.dms.auth.repository.OtpVerificationRepository;
import com.allgos.dms.auth.repository.RegistrationRequestRepository;
import com.allgos.dms.department.entity.Department;
import com.allgos.dms.department.repository.DepartmentRepository;
import com.allgos.dms.notification.entity.Notification;
import com.allgos.dms.notification.entity.NotificationType;
import com.allgos.dms.notification.repository.NotificationRepository;
import com.allgos.dms.support.AbstractIntegrationTest;
import com.allgos.dms.user.entity.UserRole;
import com.allgos.dms.user.entity.User;
import com.allgos.dms.user.entity.UserStatus;
import com.allgos.dms.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Announcements: one person telling everybody else something.
 *
 * <p>Two rules are being proved. Only an administrator may send one — a circular is the office
 * speaking to every account at once, not any one clerk. And what is sent reaches <em>everyone</em>
 * and is signed: a message that quietly went to the admins only, or that arrived without a name on
 * it, would be worse than no feature at all — an unattributable message to 150 people is how a
 * system gets used to say things nobody will own.
 */
class AnnouncementIT extends AbstractIntegrationTest {

    private static final String ADMIN_MOBILE = "9999999999"; // seeded by V3
    private static final String MEMBER_MOBILE = "9876543217";
    private static final String OTHER_MEMBER_MOBILE = "9876543218";
    private static final String PASSWORD = "Str0ngPassword!";

    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private RegistrationRequestRepository registrationRequestRepository;
    @Autowired private OtpVerificationRepository otpVerificationRepository;

    private String adminToken;
    private String memberToken;
    private UUID adminId;

    @BeforeEach
    void setUp() throws Exception {
        auditLogRepository.deleteAll();
        notificationRepository.deleteAll();
        registrationRequestRepository.deleteAll();
        otpVerificationRepository.deleteAll();
        userRepository.findByMobileNumber(MEMBER_MOBILE).ifPresent(userRepository::delete);
        userRepository.findByMobileNumber(OTHER_MEMBER_MOBILE).ifPresent(userRepository::delete);

        Department department = departmentRepository.findByActiveTrueOrderByNameAsc().getFirst();

        User admin = userRepository.findByMobileNumber(ADMIN_MOBILE).orElseThrow();
        admin.setStatus(UserStatus.ACTIVE);
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        admin.setFailedLoginCount(0);
        admin.setLockedUntil(null);
        userRepository.saveAndFlush(admin);

        adminToken = signIn(ADMIN_MOBILE);
        adminId = admin.getId();
        memberToken = registerApproveAndSignIn(MEMBER_MOBILE, "Meena Rajan", department.getId());
        registerApproveAndSignIn(OTHER_MEMBER_MOBILE, "Arun Kumar", department.getId());

        // The registrations above legitimately notify the admins; this test is about what the
        // announcement produces, so the slate is cleared between the two.
        notificationRepository.deleteAll();
    }

    @Test
    @DisplayName("a member cannot send one — the whole office is not theirs to address")
    void aMemberCannotTellTheWholeOffice() throws Exception {
        announce(memberToken, "The office will be closed on Friday for the audit.")
                .andExpect(status().isForbidden());

        // Refused outright, not quietly sent to a smaller audience.
        assertThat(notificationRepository.count()).isZero();
        assertThat(auditLogRepository.findAll())
                .noneMatch(entry -> AuditAction.ANNOUNCEMENT_SENT.equals(entry.getAction()));
    }

    @Test
    @DisplayName("an admin's message reaches every other active account, signed with their name")
    void anAdminCanTellTheWholeOffice() throws Exception {
        String message = "The office will be closed on Friday for the audit.";

        announce(adminToken, message)
                .andExpect(status().isOk())
                // Every active account except the sender — at least the two members.
                .andExpect(jsonPath("$.recipients").value(activeAccountsExcept(adminId)));

        List<Notification> sent = notificationRepository.findAll().stream()
                .filter(entry -> NotificationType.ANNOUNCEMENT.equals(entry.getType()))
                .toList();

        assertThat(sent).extracting(entry -> entry.getUser().getId())
                .containsExactlyInAnyOrderElementsOf(everyoneExcept(adminId));

        assertThat(sent).allSatisfy(entry -> {
            assertThat(entry.getTitle()).isEqualTo("Message from System Administrator");
            assertThat(entry.getBody()).isEqualTo(message);
            // Nothing to open: the message is the subject.
            assertThat(entry.getEntityRef()).isNull();
            assertThat(entry.isRead()).isFalse();
        });

        // The sender is not told what they just said.
        assertThat(sent).noneMatch(entry -> entry.getUser().getId().equals(adminId));

        // And it is on the record, with the message, so it can be traced back to whoever sent it.
        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> AuditAction.ANNOUNCEMENT_SENT.equals(entry.getAction())
                        && adminId.equals(entry.getActor().getId()));
    }

    @Test
    @DisplayName("a member reads it in their own list, unread, like any other notification")
    void itArrivesInTheOrdinaryList() throws Exception {
        announce(adminToken, "Please file the July circulars by Friday.").andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("announcement"))
                .andExpect(jsonPath("$.items[0].title").value("Message from System Administrator"))
                .andExpect(jsonPath("$.items[0].body").value("Please file the July circulars by Friday."))
                .andExpect(jsonPath("$.items[0].read").value(false));

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(jsonPath("$.unread").value(1));
    }

    @Test
    @DisplayName("a blank message is refused, and nobody is told anything")
    void blankIsRefused() throws Exception {
        announce(adminToken, "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    @DisplayName("a message longer than a notification is refused rather than truncated")
    void tooLongIsRefused() throws Exception {
        announce(adminToken, "x".repeat(501))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    @DisplayName("a second message straight after the first is refused — one click writes 150 rows")
    void theCooldownHolds() throws Exception {
        announce(adminToken, "First message").andExpect(status().isOk());

        announce(adminToken, "Second message, immediately")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ANNOUNCEMENT_COOLDOWN"));

        // Another admin is not held back by this one's cooldown. Promoted here rather than seeded
        // as a second admin, because the cooldown is per person and proving that needs two.
        User secondAdmin = userRepository.findByMobileNumber(OTHER_MEMBER_MOBILE).orElseThrow();
        secondAdmin.setRole(UserRole.ADMIN);
        userRepository.saveAndFlush(secondAdmin);

        announce(signIn(OTHER_MEMBER_MOBILE), "Unrelated message from the other admin")
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("signed out, there is nobody to send as")
    void anonymousCannotAnnounce() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/announcements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "Let me in"))))
                .andExpect(status().isUnauthorized());

        assertThat(notificationRepository.count()).isZero();
    }

    // ------------------------------------------------------------------------ helpers

    private ResultActions announce(String token, String message) throws Exception {
        return mockMvc.perform(post("/api/v1/notifications/announcements")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("message", message))));
    }

    private List<UUID> everyoneExcept(UUID senderId) {
        return userRepository.findAll().stream()
                .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                .map(User::getId)
                .filter(id -> !id.equals(senderId))
                .toList();
    }

    private int activeAccountsExcept(UUID senderId) {
        return everyoneExcept(senderId).size();
    }

    private String registerApproveAndSignIn(String mobile, String name, UUID departmentId) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", name,
                                "mobileNumber", mobile,
                                "departmentId", departmentId.toString(),
                                "designation", "Section Officer",
                                "password", PASSWORD))))
                .andExpect(status().isOk());

        User applicant = userRepository.findByMobileNumber(mobile).orElseThrow();
        applicant.setStatus(UserStatus.ACTIVE);
        userRepository.saveAndFlush(applicant);

        return signIn(mobile);
    }

    private String signIn(String mobile) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("mobileNumber", mobile, "password", PASSWORD))))
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
