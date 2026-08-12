package com.allgos.dms.user.controller;

import com.allgos.dms.auth.dto.AuthResponses.CurrentUser;
import com.allgos.dms.common.security.AuthenticatedUser;
import com.allgos.dms.user.dto.ProfileRequests;
import com.allgos.dms.user.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's own account.
 *
 * <p>Every method acts on the authenticated principal and takes no user id, so there is no
 * arrangement of parameters that edits somebody else. Changing another account is an administrative
 * act and lives in {@code AdminUserController}, behind the role check.
 */
@RestController
@RequestMapping("/api/v1/me")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * Who am I?
     *
     * <p>Session restore goes through {@code /auth/refresh}, which answers the same question but
     * mints a token as a side effect. This is the plain question, for anything that just needs the
     * current user.
     */
    @GetMapping
    public CurrentUser me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return profileService.current(principal.user());
    }

    @PatchMapping
    public CurrentUser update(
            @Valid @RequestBody ProfileRequests.UpdateProfile request,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        return profileService.update(principal.user(), request);
    }

    /**
     * Changes the password and ends every session, including this one.
     *
     * <p>204 rather than a body: the caller's token is no longer valid, so there is nothing useful
     * to return. The web app signs out and sends the user back to the login screen.
     */
    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ProfileRequests.ChangePassword request,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        profileService.changePassword(principal.user(), request);
        return ResponseEntity.noContent().build();
    }
}
