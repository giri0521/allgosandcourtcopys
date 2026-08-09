package com.allgos.dms.auth.service;

import com.allgos.dms.audit.entity.AuditAction;
import com.allgos.dms.audit.service.AuditService;
import com.allgos.dms.auth.dto.AuthRequests;
import com.allgos.dms.auth.dto.AuthResponses;
import com.allgos.dms.auth.entity.OtpPurpose;
import com.allgos.dms.auth.entity.RegistrationRequest;
import com.allgos.dms.auth.repository.RegistrationRequestRepository;
import com.allgos.dms.common.exception.ApiException;
import com.allgos.dms.common.security.JwtService;
import com.allgos.dms.department.entity.Department;
import com.allgos.dms.department.repository.DepartmentRepository;
import com.allgos.dms.notification.service.NotificationService;
import com.allgos.dms.user.entity.User;
import com.allgos.dms.user.entity.UserRole;
import com.allgos.dms.user.entity.UserStatus;
import com.allgos.dms.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration and sign-in.
 *
 * <p>Two rules run through every method here and are enforced server-side without exception:
 * a token is issued only to an approved, active account; and the first sign-in of each day must be
 * an OTP. See {@link LoginPolicyService}.
 */
@Service
public class AuthService {

    /** Consecutive password failures before the account is locked out briefly. */
    private static final int MAX_FAILED_LOGINS = 5;

    private static final java.time.Duration LOCKOUT = java.time.Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final RegistrationRequestRepository registrationRequestRepository;
    private final OtpService otpService;
    private final LoginPolicyService loginPolicy;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public AuthService(
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            RegistrationRequestRepository registrationRequestRepository,
            OtpService otpService,
            LoginPolicyService loginPolicy,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            NotificationService notificationService) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.registrationRequestRepository = registrationRequestRepository;
        this.otpService = otpService;
        this.loginPolicy = loginPolicy;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    // ------------------------------------------------------------------ registration

    /**
     * Creates a PENDING account and its review request, then sends a confirmation OTP.
     *
     * <p>The account is unusable until an admin approves it, so this never returns a token. Note the
     * role is fixed to MEMBER: an admin account can only be created by seeding or by an existing
     * admin promoting someone, so nobody can register their way to administrative access.
     */
    @Transactional
    public AuthResponses.Registered register(AuthRequests.Register request) {
        if (userRepository.existsByMobileNumber(request.mobileNumber())) {
            throw ApiException.conflict(
                    "MOBILE_ALREADY_REGISTERED", "That mobile number is already registered");
        }

        Department department = departmentRepository
                .findById(request.departmentId())
                .orElseThrow(() -> ApiException.badRequest("DEPARTMENT_INVALID", "Select a valid department"));

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setMobileNumber(request.mobileNumber());
        user.setEmail(request.email());
        user.setDesignation(request.designation());
        user.setDepartment(department);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.MEMBER);
        user.setStatus(UserStatus.PENDING);
        userRepository.save(user);

        RegistrationRequest registration = new RegistrationRequest();
        registration.setUser(user);
        registration.setRequestedRole(UserRole.MEMBER);
        registrationRequestRepository.save(registration);

        otpService.sendOtp(user.getMobileNumber(), OtpPurpose.REGISTRATION);

        auditService.record(user, AuditAction.REGISTER, "user", user.getId(),
                Map.of("department", department.getName()));
        notificationService.notifyAdminsOfNewRegistration(user);

        return new AuthResponses.Registered(
                user.getId(),
                user.getStatus(),
                "Registration received. An administrator will review your request.");
    }

    // --------------------------------------------------------------------------- OTP

    /**
     * Sends a sign-in OTP.
     *
     * <p>Succeeds identically whether or not the number belongs to an account, so this endpoint
     * cannot be used to discover who is registered. An unapproved account is still refused later, at
     * verification.
     */
    @Transactional
    public void sendLoginOtp(String mobileNumber) {
        if (userRepository.existsByMobileNumber(mobileNumber)) {
            otpService.sendOtp(mobileNumber, OtpPurpose.LOGIN);
        }
        auditService.recordAnonymous(AuditAction.OTP_SENT, Map.of("mobile", mask(mobileNumber)));
    }

    /** Confirms the mobile number given during registration. Issues no token: the account is pending. */
    @Transactional
    public void verifyRegistrationOtp(AuthRequests.VerifyOtp request) {
        otpService.verifyOtp(request.mobileNumber(), OtpPurpose.REGISTRATION, request.otp());
        userRepository
                .findByMobileNumber(request.mobileNumber())
                .ifPresent(user -> auditService.record(user, AuditAction.OTP_VERIFIED));
    }

    /**
     * The OTP sign-in path, and the only way to start the first session of the day.
     *
     * <p>On success it stamps today's date on the user, which is what subsequently permits password
     * sign-in until midnight.
     */
    @Transactional
    public AuthResponses.IssuedSession loginWithOtp(AuthRequests.VerifyOtp request) {
        User user = requireUser(request.mobileNumber());
        loginPolicy.assertCanSignIn(user);

        try {
            otpService.verifyOtp(request.mobileNumber(), OtpPurpose.LOGIN, request.otp());
        } catch (ApiException ex) {
            auditService.record(user, AuditAction.OTP_FAILED, "user", user.getId(),
                    Map.of("reason", ex.getCode()));
            throw ex;
        }

        loginPolicy.recordOtpLogin(user);
        return startSession(user, AuditAction.OTP_VERIFIED);
    }

    // ---------------------------------------------------------------------- password

    /**
     * The password sign-in path, available only after an OTP sign-in has already happened today.
     *
     * <p>The status check runs before the daily-OTP check so that a pending or disabled account is
     * told its real problem rather than being sent to fetch an OTP it can never use.
     */
    @Transactional
    public AuthResponses.IssuedSession loginWithPassword(AuthRequests.PasswordLogin request) {
        User user = requireUser(request.mobileNumber());
        loginPolicy.assertCanSignIn(user);
        loginPolicy.assertPasswordLoginAllowed(user);

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailedAttempt(user);
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Mobile number or password is incorrect");
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        return startSession(user, AuditAction.LOGIN_PASSWORD);
    }

    /** Password reset by OTP; there is no email link, because the mobile number is the identity. */
    @Transactional
    public void resetPassword(AuthRequests.ResetPassword request) {
        User user = requireUser(request.mobileNumber());
        otpService.verifyOtp(request.mobileNumber(), OtpPurpose.PASSWORD_RESET, request.otp());

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        // A reset invalidates existing sessions, in case the account was taken over.
        user.setTokenVersion(user.getTokenVersion() + 1);

        auditService.record(user, AuditAction.PASSWORD_CHANGED);
    }

    @Transactional
    public void sendPasswordResetOtp(String mobileNumber) {
        if (userRepository.existsByMobileNumber(mobileNumber)) {
            otpService.sendOtp(mobileNumber, OtpPurpose.PASSWORD_RESET);
        }
    }

    // ------------------------------------------------------------------------ tokens

    /**
     * Exchanges a refresh token for a new access token.
     *
     * <p>Re-checks status and token version, so an account disabled a moment ago cannot extend its
     * session, and "log out from all devices" takes effect immediately.
     */
    @Transactional
    public AuthResponses.Session refresh(String refreshToken) {
        Claims claims;
        try {
            claims = jwtService.parse(refreshToken);
        } catch (JwtException | IllegalArgumentException ex) {
            throw ApiException.unauthorized("REFRESH_INVALID", "Your session has expired. Please sign in again.");
        }

        if (!jwtService.isRefreshToken(claims)) {
            throw ApiException.unauthorized("REFRESH_INVALID", "Your session has expired. Please sign in again.");
        }

        UUID userId = jwtService.userIdOf(claims);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("REFRESH_INVALID", "Please sign in again"));

        loginPolicy.assertCanSignIn(user);

        if (user.getTokenVersion() != jwtService.tokenVersionOf(claims)) {
            throw ApiException.unauthorized("SESSION_REVOKED", "You were signed out. Please sign in again.");
        }

        return new AuthResponses.Session(
                jwtService.issueAccessToken(user),
                jwtService.accessTokenTtl().toSeconds(),
                AuthResponses.CurrentUser.from(user));
    }

    @Transactional
    public void logout(User user) {
        auditService.record(user, AuditAction.LOGOUT);
    }

    /** Invalidates every token already issued to this user by moving the token version forward. */
    @Transactional
    public void logoutAll(User user) {
        User managed = userRepository.findById(user.getId()).orElseThrow();
        managed.setTokenVersion(managed.getTokenVersion() + 1);
        auditService.record(managed, AuditAction.LOGOUT_ALL);
    }

    // ------------------------------------------------------------------------ helpers

    private AuthResponses.IssuedSession startSession(User user, String auditAction) {
        user.setLastLoginAt(Instant.now());
        auditService.record(user, auditAction, "user", user.getId(), null);

        AuthResponses.Session session = new AuthResponses.Session(
                jwtService.issueAccessToken(user),
                jwtService.accessTokenTtl().toSeconds(),
                AuthResponses.CurrentUser.from(user));

        return new AuthResponses.IssuedSession(session, jwtService.issueRefreshToken(user));
    }

    /**
     * Deliberately reports the same failure whether the number is unknown or the password is wrong,
     * so sign-in cannot be used to discover which numbers are registered.
     */
    private User requireUser(String mobileNumber) {
        return userRepository
                .findByMobileNumber(mobileNumber)
                .orElseThrow(() -> {
                    auditService.recordAnonymous(
                            AuditAction.LOGIN_FAILED, Map.of("mobile", mask(mobileNumber), "reason", "unknown_user"));
                    return ApiException.unauthorized(
                            "INVALID_CREDENTIALS", "Mobile number or password is incorrect");
                });
    }

    private void registerFailedAttempt(User user) {
        int failures = user.getFailedLoginCount() + 1;
        user.setFailedLoginCount(failures);
        if (failures >= MAX_FAILED_LOGINS) {
            user.setLockedUntil(Instant.now().plus(LOCKOUT));
        }
        auditService.record(user, AuditAction.LOGIN_FAILED, "user", user.getId(),
                Map.of("failedAttempts", failures));
    }

    private static String mask(String mobileNumber) {
        return mobileNumber == null || mobileNumber.length() < 4
                ? "****"
                : "******" + mobileNumber.substring(mobileNumber.length() - 4);
    }
}
