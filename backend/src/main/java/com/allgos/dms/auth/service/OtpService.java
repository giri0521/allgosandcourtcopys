package com.allgos.dms.auth.service;

import com.allgos.dms.auth.entity.OtpPurpose;
import com.allgos.dms.auth.entity.OtpVerification;
import com.allgos.dms.auth.repository.OtpVerificationRepository;
import com.allgos.dms.common.config.AppProperties;
import com.allgos.dms.common.exception.ApiException;
import com.allgos.dms.common.sms.OtpProvider;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and verifies one-time codes.
 *
 * <p>OTP is the only credential that can start a session — a password is never accepted until an OTP
 * has already succeeded that day — so the limits enforced here are the application's entire front
 * door. Weakening any of them is a release blocker, not a tuning decision:
 *
 * <ul>
 *   <li>the code is stored only as a hash, so a database read cannot be replayed
 *   <li>codes expire (default 5 minutes) and are single-use
 *   <li>a fixed number of wrong guesses burns the code rather than allowing an exhaustive search
 *   <li>sends are throttled per mobile number, both by cooldown and by hourly volume
 * </ul>
 */
@Service
public class OtpService {

    /** Hourly ceiling on codes sent to one number, above the per-send cooldown. */
    private static final int MAX_SENDS_PER_HOUR = 5;

    private final OtpVerificationRepository otpRepository;
    private final OtpProvider otpProvider;
    private final PasswordEncoder passwordEncoder;
    private final FailedAttemptRecorder failedAttemptRecorder;
    private final AppProperties.Otp config;
    private final SecureRandom random = new SecureRandom();

    public OtpService(
            OtpVerificationRepository otpRepository,
            OtpProvider otpProvider,
            PasswordEncoder passwordEncoder,
            FailedAttemptRecorder failedAttemptRecorder,
            AppProperties properties) {
        this.otpRepository = otpRepository;
        this.otpProvider = otpProvider;
        this.passwordEncoder = passwordEncoder;
        this.failedAttemptRecorder = failedAttemptRecorder;
        this.config = properties.otp();
    }

    /**
     * Generates a code, stores its hash and hands the plaintext to the SMS provider.
     *
     * <p>Deliberately says nothing about whether the mobile number belongs to a real account: the
     * caller decides that, so this endpoint cannot be used to enumerate users.
     */
    @Transactional
    public void sendOtp(String mobileNumber, OtpPurpose purpose) {
        enforceSendLimits(mobileNumber);

        String code = generateCode();

        OtpVerification verification = new OtpVerification();
        verification.setMobileNumber(mobileNumber);
        verification.setOtpHash(passwordEncoder.encode(code));
        verification.setPurpose(purpose);
        verification.setExpiresAt(Instant.now().plus(config.ttl()));
        otpRepository.save(verification);

        otpProvider.sendOtp(mobileNumber, code);
    }

    /**
     * Checks a submitted code and consumes it on success.
     *
     * @throws ApiException if there is no code, or it has expired, been used, run out of attempts,
     *     or simply does not match
     */
    @Transactional
    public void verifyOtp(String mobileNumber, OtpPurpose purpose, String submittedCode) {
        OtpVerification verification = otpRepository
                .findFirstByMobileNumberAndPurposeOrderByCreatedAtDesc(mobileNumber, purpose)
                .orElseThrow(() -> ApiException.badRequest(
                        "OTP_NOT_FOUND", "Request a new OTP and try again"));

        if (verification.isUsed()) {
            throw ApiException.badRequest("OTP_ALREADY_USED", "That OTP has already been used");
        }
        if (verification.isExpired()) {
            throw ApiException.badRequest("OTP_EXPIRED", "That OTP has expired. Request a new one.");
        }
        if (verification.getAttemptCount() >= config.maxAttempts()) {
            throw ApiException.tooManyRequests(
                    "OTP_ATTEMPTS_EXCEEDED", "Too many incorrect attempts. Request a new OTP.");
        }

        if (!passwordEncoder.matches(submittedCode, verification.getOtpHash())) {
            // Committed separately: the exception below rolls this transaction back, and a lost
            // increment would leave OTP guessing unbounded.
            failedAttemptRecorder.recordOtpFailure(verification.getId());
            throw ApiException.badRequest("OTP_INVALID", "That OTP is not correct");
        }

        // Likewise committed on its own, so a later failure cannot resurrect a spent code.
        failedAttemptRecorder.markOtpVerified(verification.getId());
    }

    private void enforceSendLimits(String mobileNumber) {
        otpRepository
                .findFirstByMobileNumberOrderByCreatedAtDesc(mobileNumber)
                .ifPresent(latest -> rejectIfWithinCooldown(latest.getCreatedAt()));

        long sentInLastHour =
                otpRepository.countByMobileNumberAndCreatedAtAfter(mobileNumber, Instant.now().minus(Duration.ofHours(1)));
        if (sentInLastHour >= MAX_SENDS_PER_HOUR) {
            throw ApiException.tooManyRequests(
                    "OTP_SEND_LIMIT", "Too many OTP requests. Please try again later.");
        }
    }

    private void rejectIfWithinCooldown(Instant lastSentAt) {
        Instant nextAllowed = lastSentAt.plus(config.resendCooldown());
        if (Instant.now().isBefore(nextAllowed)) {
            throw ApiException.tooManyRequests(
                    "OTP_COOLDOWN", "Please wait before requesting another OTP");
        }
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder(config.length());
        for (int i = 0; i < config.length(); i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }
}
