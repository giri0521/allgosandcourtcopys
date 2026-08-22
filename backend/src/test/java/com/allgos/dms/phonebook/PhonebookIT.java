package com.allgos.dms.phonebook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.allgos.dms.audit.entity.AuditAction;
import com.allgos.dms.audit.repository.AuditLogRepository;
import com.allgos.dms.auth.repository.RegistrationRequestRepository;
import com.allgos.dms.department.entity.Department;
import com.allgos.dms.department.repository.DepartmentRepository;
import com.allgos.dms.phonebook.repository.PhonebookContactRepository;
import com.allgos.dms.support.AbstractIntegrationTest;
import com.allgos.dms.user.entity.User;
import com.allgos.dms.user.entity.UserStatus;
import com.allgos.dms.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
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
 * The phonebook: two books, read by everyone and written by administrators.
 *
 * <p>The rule worth proving is the asymmetry. A directory nobody can read is a filing cabinet, so
 * every approved member sees both books in full — including departments that are not their own,
 * which is the whole point. But a member cannot add, correct or remove a number, because a wrong
 * one in a shared book is rung for months before anybody traces where it came from.
 */
class PhonebookIT extends AbstractIntegrationTest {

    private static final String ADMIN_MOBILE = "9999999999"; // seeded by V3
    private static final String MEMBER_MOBILE = "9876543219";
    private static final String PASSWORD = "Str0ngPassword!";

    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private PhonebookContactRepository contactRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private RegistrationRequestRepository registrationRequestRepository;

    private String adminToken;
    private String memberToken;
    private Department department;

    @BeforeEach
    void setUp() throws Exception {
        contactRepository.deleteAll();
        auditLogRepository.deleteAll();
        registrationRequestRepository.deleteAll();
        userRepository.findByMobileNumber(MEMBER_MOBILE).ifPresent(userRepository::delete);

        department = departmentRepository.findByActiveTrueOrderByNameAsc().getFirst();

        User admin = userRepository.findByMobileNumber(ADMIN_MOBILE).orElseThrow();
        admin.setStatus(UserStatus.ACTIVE);
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        admin.setFailedLoginCount(0);
        admin.setLockedUntil(null);
        userRepository.saveAndFlush(admin);
        adminToken = signIn(ADMIN_MOBILE);

        memberToken = registerApproveAndSignIn(MEMBER_MOBILE, "Meena Rajan");
    }

    // ------------------------------------------------------------------ department book

    @Test
    @DisplayName("an admin adds a department number and every member can read it")
    void departmentContactsAreReadableByEveryone() throws Exception {
        create(adminToken, departmentContact("Dr. S. Kumar", "Joint Director", "044-2345 6789"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Dr. S. Kumar"))
                .andExpect(jsonPath("$.departmentName").value(department.getName()))
                // The other book's fields stay empty on a department entry.
                .andExpect(jsonPath("$.taluk").doesNotExist())
                .andExpect(jsonPath("$.role").doesNotExist());

        mockMvc.perform(get("/api/v1/phonebook/departments").header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].departmentName").value(department.getName()))
                .andExpect(jsonPath("$[0].contacts[0].fullName").value("Dr. S. Kumar"))
                .andExpect(jsonPath("$[0].contacts[0].designation").value("Joint Director"))
                // A landline with an STD code, which the login format would have refused.
                .andExpect(jsonPath("$[0].contacts[0].phoneNumber").value("044-2345 6789"));
    }

    @Test
    @DisplayName("contacts are grouped under their department, in name order")
    void departmentContactsAreGrouped() throws Exception {
        create(adminToken, departmentContact("Zubaida Begum", "Superintendent", "9876500001"));
        create(adminToken, departmentContact("Anand Raj", "Section Officer", "9876500002"));

        mockMvc.perform(get("/api/v1/phonebook/departments").header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].contacts.length()").value(2))
                .andExpect(jsonPath("$[0].contacts[0].fullName").value("Anand Raj"))
                .andExpect(jsonPath("$[0].contacts[1].fullName").value("Zubaida Begum"));
    }

    // ----------------------------------------------------------------------- taluk book

    @Test
    @DisplayName("a taluk lists its tahsildar apart from its group members")
    void talukContactsAreSplitByRole() throws Exception {
        create(adminToken, talukContact("K. Ravi", "TAHSILDAR", "Coimbatore North", "94430 11111"));
        create(adminToken, talukContact("P. Latha", "GROUP_MEMBER", "Coimbatore North", "94430 22222"));
        create(adminToken, talukContact("A. Suresh", "GROUP_MEMBER", "Coimbatore North", "94430 33333"));
        create(adminToken, talukContact("M. Devi", "TAHSILDAR", "Avinashi", "94430 44444"));

        mockMvc.perform(get("/api/v1/phonebook/taluks").header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isOk())
                // Taluks in name order: Avinashi before Coimbatore North.
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].taluk").value("Avinashi"))
                .andExpect(jsonPath("$[0].tahsildars[0].fullName").value("M. Devi"))
                .andExpect(jsonPath("$[0].groupMembers.length()").value(0))
                .andExpect(jsonPath("$[1].taluk").value("Coimbatore North"))
                .andExpect(jsonPath("$[1].tahsildars.length()").value(1))
                .andExpect(jsonPath("$[1].tahsildars[0].fullName").value("K. Ravi"))
                .andExpect(jsonPath("$[1].groupMembers.length()").value(2))
                .andExpect(jsonPath("$[1].groupMembers[0].fullName").value("A. Suresh"));
    }

    @Test
    @DisplayName("a taluk is created by naming it, with no list to set up first")
    void taluksNeedNoSetup() throws Exception {
        create(adminToken, talukContact("New Officer", "TAHSILDAR", "Mettupalayam", "94430 55555"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taluk").value("Mettupalayam"))
                .andExpect(jsonPath("$.departmentId").doesNotExist());
    }

    // ---------------------------------------------------------------------- maintenance

    @Test
    @DisplayName("an admin corrects a number, and the correction is on the record")
    void contactsCanBeCorrected() throws Exception {
        UUID id = idOf(create(adminToken, departmentContact("R. Meena", "Section Officer", "9876500003")));

        Map<String, Object> corrected = departmentContact("R. Meena", "Superintendent", "044-2345 0000");
        mockMvc.perform(put("/api/v1/admin/phonebook/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corrected)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designation").value("Superintendent"))
                .andExpect(jsonPath("$.phoneNumber").value("044-2345 0000"));

        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> AuditAction.PHONEBOOK_CONTACT_UPDATED.equals(entry.getAction()));
    }

    @Test
    @DisplayName("a contact moved between books does not keep the other book's fields")
    void movingBetweenBooksClearsTheOtherSide() throws Exception {
        UUID id = idOf(create(adminToken, departmentContact("S. Anand", "Section Officer", "9876500004")));

        mockMvc.perform(put("/api/v1/admin/phonebook/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                talukContact("S. Anand", "TAHSILDAR", "Pollachi", "9876500004"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taluk").value("Pollachi"))
                .andExpect(jsonPath("$.departmentId").doesNotExist());

        // Gone from the book it left, present in the one it joined.
        mockMvc.perform(get("/api/v1/phonebook/departments").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/phonebook/taluks").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(jsonPath("$[0].tahsildars[0].fullName").value("S. Anand"));
    }

    @Test
    @DisplayName("an admin removes a number, and it says who was removed")
    void contactsCanBeRemoved() throws Exception {
        UUID id = idOf(create(adminToken, departmentContact("Wrong Number", "Clerk", "9876500005")));

        mockMvc.perform(delete("/api/v1/admin/phonebook/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());

        assertThat(contactRepository.count()).isZero();
        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> AuditAction.PHONEBOOK_CONTACT_REMOVED.equals(entry.getAction()));
    }

    // ------------------------------------------------------------------------ refusals

    @Test
    @DisplayName("a member reads the book but cannot write to it")
    void membersCannotMaintainTheBook() throws Exception {
        UUID id = idOf(create(adminToken, departmentContact("Dr. S. Kumar", "Joint Director", "9876500006")));

        create(memberToken, departmentContact("Made Up", "Clerk", "9876500007"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(put("/api/v1/admin/phonebook/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                departmentContact("Dr. S. Kumar", "Clerk", "9876500008"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/admin/phonebook/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isForbidden());

        // Nothing the member sent changed anything.
        assertThat(contactRepository.count()).isEqualTo(1);
        assertThat(contactRepository.findAll().getFirst().getDesignation()).isEqualTo("Joint Director");
    }

    @Test
    @DisplayName("a department entry with no department, and a taluk entry with no role, are refused")
    void theShapeOfEachBookIsEnforced() throws Exception {
        Map<String, Object> noDepartment = departmentContact("Nobody", "Clerk", "9876500009");
        noDepartment.remove("departmentId");
        create(adminToken, noDepartment)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_REQUIRED"));

        Map<String, Object> noRole = talukContact("Nobody", "TAHSILDAR", "Pollachi", "9876500010");
        noRole.remove("role");
        create(adminToken, noRole)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROLE_REQUIRED"));

        Map<String, Object> noTaluk = talukContact("Nobody", "TAHSILDAR", "  ", "9876500011");
        create(adminToken, noTaluk)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TALUK_REQUIRED"));

        assertThat(contactRepository.count()).isZero();
    }

    @Test
    @DisplayName("a name with no number is refused, but an office landline is not")
    void phoneNumbersAreCheckedWithoutInsistingOnAMobile() throws Exception {
        create(adminToken, departmentContact("No Number", "Clerk", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        create(adminToken, departmentContact("Reception", "Front office", "044-2567 8900"))
                .andExpect(status().isOk());
        create(adminToken, departmentContact("Control room", "24 hours", "+91 44 2567 8901"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("signed out, the book is not readable at all")
    void theBookIsNotPublic() throws Exception {
        mockMvc.perform(get("/api/v1/phonebook/departments")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/phonebook/taluks")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------------- helpers

    private Map<String, Object> departmentContact(String name, String designation, String phone) {
        Map<String, Object> body = new HashMap<>();
        body.put("kind", "DEPARTMENT");
        body.put("fullName", name);
        body.put("designation", designation);
        body.put("phoneNumber", phone);
        body.put("departmentId", department.getId().toString());
        return body;
    }

    private Map<String, Object> talukContact(String name, String role, String taluk, String phone) {
        Map<String, Object> body = new HashMap<>();
        body.put("kind", "TALUK");
        body.put("fullName", name);
        body.put("phoneNumber", phone);
        body.put("taluk", taluk);
        body.put("role", role);
        return body;
    }

    private ResultActions create(String token, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/phonebook")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private UUID idOf(ResultActions created) throws Exception {
        String body = created.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
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
