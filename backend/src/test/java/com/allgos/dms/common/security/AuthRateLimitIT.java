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
 *
 * <p><b>Each test declares its own client address.</b> The limiter is a singleton in the application
 * context with a one-minute window, so without this the first method to run spends the allowance and
 * every later one starts already throttled — which is what happened the first time these ran. Since
 * {@code ClientIp} prefers {@code X-Forwarded-For}, giving each test its own address isolates them
 * and exercises the per-key behaviour at the same time.
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
        String caller = "203.0.113.10";

        for (int attempt = 1; attempt <= 3; attempt++) {
            // Wrong credentials, so these are refused on their merits — but they are *answered*,
            // which is what distinguishes them from being throttled.
            passwordLogin(caller).andExpect(status().isUnauthorized());
        }

        passwordLogin(caller)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("the allowance covers the whole of /auth, not one endpoint at a time")
    void countsEveryAuthEndpointTogether() throws Exception {
        String caller = "203.0.113.11";

        sendOtp(caller).andExpect(status().isAccepted());
        sendOtp(caller).andExpect(status().isAccepted());
        passwordLogin(caller).andExpect(status().isUnauthorized());

        // A fourth call to a *different* auth endpoint is still refused: an attacker who could
        // reset the count by alternating endpoints would have no limit at all.
        sendOtp(caller)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    @DisplayName("one caller's exhausted allowance does not throttle anybody else")
    void isolatesCallers() throws Exception {
        String flooding = "203.0.113.12";
        String innocent = "203.0.113.13";

        for (int attempt = 1; attempt <= 4; attempt++) {
            passwordLogin(flooding);
        }
        passwordLogin(flooding).andExpect(status().isTooManyRequests());

        // Otherwise one attacker could lock the whole office out by exhausting a shared counter.
        passwordLogin(innocent).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the throttle applies only to /auth — ordinary work is never slowed")
    void doesNotThrottleTheRestOfTheApi() throws Exception {
        for (int attempt = 1; attempt <= 10; attempt++) {
            // Unauthenticated, so 401 — but never 429, however many times it is called.
            mockMvc.perform(get("/api/v1/departments").header("X-Forwarded-For", "203.0.113.14"))
                    .andExpect(status().isUnauthorized());
        }
    }

    private org.springframework.test.web.servlet.ResultActions passwordLogin(String caller) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .header("X-Forwarded-For", caller)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("mobileNumber", "9123456789", "password", "not-the-password"))));
    }

    private org.springframework.test.web.servlet.ResultActions sendOtp(String caller) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/otp/send")
                .header("X-Forwarded-For", caller)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("mobileNumber", "9123456789"))));
    }
}
