package com.allgos.dms.auth.controller;

import com.allgos.dms.auth.dto.AuthRequests;
import com.allgos.dms.auth.dto.AuthResponses;
import com.allgos.dms.auth.service.AuthService;
import com.allgos.dms.common.exception.ApiException;
import com.allgos.dms.common.security.AuthenticatedUser;
import com.allgos.dms.common.security.JwtService;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints.
 *
 * <p>Sign-in is by password. The one OTP left authorises a password reset, and issues no session of
 * its own.
 *
 * <p>The access token is returned in the body for the web app to hold in memory; the refresh token
 * is set as an httpOnly, SameSite=Strict cookie so that script running in the page cannot read it.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String REFRESH_COOKIE = "refresh_token";

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public AuthResponses.Registered register(@Valid @RequestBody AuthRequests.Register request) {
        return authService.register(request);
    }

    /** Sign-in. Mobile number and password; approval and lockout are checked server-side. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponses.Session> login(@Valid @RequestBody AuthRequests.PasswordLogin request) {
        return sessionResponse(authService.loginWithPassword(request));
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody AuthRequests.SendOtp request) {
        authService.sendPasswordResetOtp(request.mobileNumber());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody AuthRequests.ResetPassword request) {
        authService.resetPassword(request);
        return clearedRefreshCookieResponse();
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponses.Session> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw ApiException.unauthorized("REFRESH_MISSING", "Please sign in again");
        }
        return ResponseEntity.ok(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal != null) {
            authService.logout(principal.user());
        }
        return clearedRefreshCookieResponse();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal AuthenticatedUser principal) {
        authService.logoutAll(principal.user());
        return clearedRefreshCookieResponse();
    }

    // ------------------------------------------------------------------------ helpers

    private ResponseEntity<AuthResponses.Session> sessionResponse(AuthResponses.IssuedSession issued) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(issued.refreshToken()).toString())
                .body(issued.session());
    }

    private ResponseCookie refreshCookie(String value) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(jwtService.refreshTokenTtl())
                .build();
    }

    /** 204 with the refresh cookie expired, so the browser drops it immediately. */
    private ResponseEntity<Void> clearedRefreshCookieResponse() {
        ResponseCookie cleared = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(Duration.ZERO)
                .build();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cleared.toString())
                .build();
    }
}
