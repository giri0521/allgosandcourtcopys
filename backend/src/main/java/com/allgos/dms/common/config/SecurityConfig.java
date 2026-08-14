package com.allgos.dms.common.config;

import com.allgos.dms.common.dto.ApiErrorResponse;
import com.allgos.dms.common.security.AuthRateLimitFilter;
import com.allgos.dms.common.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Everything is denied unless explicitly permitted.
 *
 * <p>Only the authentication endpoints and the health probe are public. Admin-only routes are
 * additionally guarded by {@code @PreAuthorize} on the controllers — a menu item hidden in the web
 * app is never the thing that protects an endpoint.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public SecurityConfig(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http, JwtAuthenticationFilter jwtFilter, AuthRateLimitFilter rateLimitFilter)
            throws Exception {

        return http.csrf(csrf -> csrf.disable()) // stateless bearer tokens; no cookie-authenticated writes
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        // Spring Security already sends nosniff, X-Frame-Options: DENY and
                        // no-store. These are the four it does not.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(Duration.ofDays(365).toSeconds()))
                        // This is a JSON API: nothing it returns should ever be treated as a
                        // document that can load anything. If a response is somehow rendered — a
                        // reflected error, a file served by mistake — it can reach nothing.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; frame-ancestors 'none'; sandbox"))
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // Nothing here needs a camera, a microphone or a location, and saying so
                        // stops an embedded context asking on our behalf.
                        .permissionsPolicy(permissions -> permissions.policy(
                                "camera=(), microphone=(), geolocation=(), payment=()")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) ->
                                writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        "UNAUTHENTICATED", "Please sign in to continue"))
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(response, HttpServletResponse.SC_FORBIDDEN,
                                        "ACCESS_DENIED", "You do not have access to this resource")))
                // Before the JWT filter, and therefore before any password or OTP work: a refused
                // request must cost the server nothing beyond a map lookup.
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtFilter, AuthRateLimitFilter.class)
                .build();
    }

    /**
     * Errors raised inside the filter chain bypass GlobalExceptionHandler, so they are written here
     * in the same shape the web app expects everywhere else.
     */
    private void writeError(HttpServletResponse response, int status, String code, String message) {
        try {
            response.setStatus(status);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), ApiErrorResponse.of(code, message));
        } catch (Exception ignored) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.cors().allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true); // the refresh token travels as an httpOnly cookie

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
