package com.allgos.dms.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.allgos.dms.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * The per-address throttle on the authentication endpoints, over HTTP.
 *
 * <p>Turned on here with an allowance of three, because the rest of the suite runs with it off —
 * every other integration test signs several accounts in from 127.0.0.1 within a second, which is
 * indistinguishable from the attack this defends against.
 *
 * <p>What matters is not only that the fourth request is refused, but *where*: before any password
 * or OTP work happens, so a flood costs the server a map lookup rather than a BCrypt verification.
 */
@TestPropertySource(
        properties = {
            "app.rate-limit.enabled=true",
            "app.rate-limit.auth-requests-per-minute=3",
        })
class AuthRateLimitIT extends AbstractIntegrationTest {

    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("a flood of sign-in attempts is refused with 429 and a Retry-After")
    void refusesTooManyAuthRequests() throws Exception {
        for (int attempt = 1; attempt <= 3; attempt++) {
            // Wrong credentials, so these are refused on their merits — but they are *answered*,
            // which is what distinguishes them from being throttled.
            passwordLogin().andExpect(status().isUnauthorized());
        }

        passwordLogin()
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("the allowance covers the whole of /auth, not one endpoint at a time")
    void countsEveryAuthEndpointTogether() throws Exception {
        sendOtp().andExpect(status().isAccepted());
        sendOtp().andExpect(status().isAccepted());
        passwordLogin().andExpect(status().isUnauthorized());

        // A fourth call to a *different* auth endpoint is still refused: an attacker who could
        // reset the count by alternating endpoints would have no limit at all.
        sendOtp()
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    @DisplayName("the throttle applies only to /auth — ordinary work is never slowed")
    void doesNotThrottleTheRestOfTheApi() throws Exception {
        for (int attempt = 1; attempt <= 10; attempt++) {
            // Unauthenticated, so 401 — but never 429, however many times it is called.
            mockMvc.perform(get("/api/v1/departments")).andExpect(status().isUnauthorized());
        }
    }

    private org.springframework.test.web.servlet.ResultActions passwordLogin() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("mobileNumber", "9123456789", "password", "not-the-password"))));
    }

    private org.springframework.test.web.servlet.ResultActions sendOtp() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/otp/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("mobileNumber", "9123456789"))));
    }
}
