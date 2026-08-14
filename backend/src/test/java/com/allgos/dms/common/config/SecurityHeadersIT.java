package com.allgos.dms.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.allgos.dms.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The headers every response carries.
 *
 * <p>These are the sort of thing that gets configured once, works, and is then silently removed by
 * a later refactor of the security chain — nothing else in the suite would notice. Asserting them
 * over HTTP costs nothing and means a regression fails a build rather than a penetration test.
 */
class SecurityHeadersIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("an unauthenticated response still carries every hardening header")
    void headersArePresentOnRefusals() throws Exception {
        // A 401 is the response an unauthenticated attacker sees most, so it is the one to check.
        mockMvc.perform(get("/api/v1/departments"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string(
                        "Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; sandbox"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string(
                        "Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()"));
    }

    @Test
    @DisplayName("HSTS is sent over HTTPS and withheld over plain HTTP")
    void hstsIsSentOnlyOverHttps() throws Exception {
        // Sending HSTS over plain HTTP is meaningless — a browser ignores it — and would break
        // local development over http://localhost. Spring Security withholds it by design.
        mockMvc.perform(get("/api/v1/departments"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));

        mockMvc.perform(get("/api/v1/departments").secure(true))
                .andExpect(header().string(
                        "Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"));
    }

    @Test
    @DisplayName("a public endpoint carries them too")
    void headersArePresentOnPublicRoutes() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
    }
}
