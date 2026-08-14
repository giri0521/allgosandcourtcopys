package com.allgos.dms.common.security;

import com.allgos.dms.common.config.AppProperties;
import com.allgos.dms.common.dto.ApiErrorResponse;
import com.allgos.dms.common.web.ClientIp;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Caps how often one network address may call the authentication endpoints.
 *
 * <p><b>Why this exists.</b> The application already bounds brute force per <em>account</em>: OTP
 * codes expire and are attempt-limited, and password failures lock the account. Neither of those
 * bounds an attacker who spreads attempts across many accounts — a script trying one password
 * against ten thousand mobile numbers never trips a per-account counter, and every attempt costs a
 * BCrypt verification. This is the per-caller bound that the account-level ones cannot provide.
 *
 * <p>Scoped to {@code /api/v1/auth/**} on purpose. Those are the only unauthenticated routes; past
 * them a caller has already presented a token, and throttling ordinary work would degrade a busy
 * office for no security gain.
 *
 * <p>The address comes from {@link ClientIp}, which prefers {@code X-Forwarded-For}. That header is
 * client-controllable, so behind a proxy that does not overwrite it a determined attacker can
 * rotate the value and evade this. **The reverse proxy must set X-Forwarded-For itself** — the
 * deployment note in the handoff says so, and that is the assumption this filter is built on.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);
    private static final String AUTH_PATH = "/api/v1/auth/";

    private final AppProperties.RateLimit config;
    private final ObjectMapper objectMapper;
    private final RateLimiter limiter;

    public AuthRateLimitFilter(AppProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.config = properties.rateLimit();
        this.objectMapper = objectMapper;
        this.limiter = new RateLimiter(
                config.authRequestsPerMinute(), Duration.ofMinutes(1), clock);
    }

    /**
     * Runs only for the authentication routes, so every other request skips the map lookup
     * entirely.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !config.enabled() || !request.getRequestURI().startsWith(AUTH_PATH);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String caller = ClientIp.current();
        if (caller == null) {
            caller = request.getRemoteAddr();
        }

        if (limiter.tryAcquire(caller)) {
            filterChain.doFilter(request, response);
            return;
        }

        Duration retryAfter = limiter.retryAfter(caller);

        // Worth a log line: a legitimate office never reaches this, so every occurrence is either
        // an attack or a misconfigured proxy collapsing every user onto one address.
        log.warn(
                "Rate limit reached for {} on {} — refusing for another {}s",
                caller,
                request.getRequestURI(),
                retryAfter.toSeconds());

        // The servlet API's SC_ constants stop at 505; 429 came later, in RFC 6585.
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, retryAfter.toSeconds())));
        objectMapper.writeValue(
                response.getWriter(),
                ApiErrorResponse.of(
                        "RATE_LIMITED", "Too many attempts from this connection. Please wait and try again."));
    }
}
