package com.allgos.dms.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.allgos.dms.support.AbstractIntegrationTest;
import com.allgos.dms.user.entity.User;
import com.allgos.dms.user.entity.UserStatus;
import com.allgos.dms.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The CSV export, over a real socket.
 *
 * <p><b>Why this class exists at all.</b> Every other integration test here runs through MockMvc,
 * which calls the filter chain in one pass on the calling thread. That is enough for almost
 * everything and blind to one whole class of failure: a response that Spring returns *before* the
 * body is written — a {@code StreamingResponseBody} — is finished on a second, asynchronous
 * dispatch through the same security filters, with an empty {@code SecurityContext}, because the
 * JWT filter is a {@code OncePerRequestFilter} and those skip async dispatches by default. The
 * authorization filter then denies the already-half-sent response, Tomcat cannot turn that into an
 * error page because the headers have gone, and the browser is left holding a download that never
 * completes.
 *
 * <p>That is exactly what the export did in the running application while
 * {@code AdminMonitoringIT.exportIsARealCsv} passed. A real port is the only way to see it, so this
 * one test pays for a second application context.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ReportExportHttpIT {

    private static final String ADMIN_MOBILE = "9999999999"; // seeded by V3
    private static final String PASSWORD = "Str0ngPassword!";

    @Autowired private TestRestTemplate rest;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    /** The same container every other integration test uses, rather than a second Postgres. */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", AbstractIntegrationTest.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", AbstractIntegrationTest.POSTGRES::getUsername);
        registry.add("spring.datasource.password", AbstractIntegrationTest.POSTGRES::getPassword);
    }

    private String token;

    @BeforeEach
    void signIn() {
        User admin = userRepository.findByMobileNumber(ADMIN_MOBILE).orElseThrow();
        admin.setStatus(UserStatus.ACTIVE);
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        admin.setFailedLoginCount(0);
        admin.setLockedUntil(null);
        userRepository.saveAndFlush(admin);

        ResponseEntity<String> session = rest.postForEntity(
                "/api/v1/auth/login",
                new HttpEntity<>(
                        Map.of("mobileNumber", ADMIN_MOBILE, "password", PASSWORD), json()),
                String.class);

        assertThat(session.getStatusCode().is2xxSuccessful())
                .as("sign-in: %s", session.getBody())
                .isTrue();

        try {
            token = objectMapper.readTree(session.getBody()).get("accessToken").asText();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not read the access token", ex);
        }
    }

    @Test
    @DisplayName("every report downloads over a real connection, headers and body intact")
    void everyReportExports() {
        for (String type : new String[] {"departments", "uploaders", "monthly"}) {
            ResponseEntity<byte[]> response = rest.exchange(
                    "/api/v1/admin/reports/export?type=" + type,
                    HttpMethod.GET,
                    new HttpEntity<>(bearer()),
                    byte[].class);

            assertThat(response.getStatusCode().value()).as("%s status", type).isEqualTo(200);

            byte[] body = response.getBody();
            assertThat(body).as("%s body", type).isNotNull();
            // The byte-order mark, without which Excel reads the Tamil names as mojibake.
            assertThat(body[0]).as("%s starts with the BOM", type).isEqualTo((byte) 0xEF);
            assertThat(new String(body, StandardCharsets.UTF_8))
                    .as("%s has a header row", type)
                    .contains("\"");

            assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                    .as("%s is offered as a file", type)
                    .contains("attachment")
                    .contains(type);
        }
    }

    @Test
    @DisplayName("an unknown report is refused as JSON, not as a half-written file")
    void unknownReportIsARefusal() {
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/admin/reports/export?type=nonsense",
                HttpMethod.GET,
                new HttpEntity<>(bearer()),
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).contains("REPORT_UNKNOWN");
    }

    @Test
    @DisplayName("without a token it is refused before any of the report is built")
    void anonymousIsRefused() {
        ResponseEntity<String> response =
                rest.getForEntity("/api/v1/admin/reports/export?type=departments", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    private HttpHeaders bearer() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private static HttpHeaders json() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
