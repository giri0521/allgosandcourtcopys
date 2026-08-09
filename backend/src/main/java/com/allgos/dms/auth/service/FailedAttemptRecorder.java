package com.allgos.dms.auth.service;

import com.allgos.dms.auth.repository.OtpVerificationRepository;
import com.allgos.dms.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the counters that bound brute-force attempts.
 *
 * <p>These updates must survive the exception that follows them. A rejected OTP or password throws,
 * which rolls the caller's transaction back — if the counter were incremented in that same
 * transaction it would be discarded, leaving OTP guessing and password guessing effectively
 * unlimited. Each method therefore commits in its own transaction, which also means they live in a
 * separate bean: a self-invocation would bypass the proxy and silently lose the new transaction.
 */
@Service
public class FailedAttemptRecorder {

    private static final int MAX_FAILED_LOGINS = 5;
    private static final Duration LOCKOUT = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final OtpVerificationRepository otpRepository;

    public FailedAttemptRecorder(UserRepository userRepository, OtpVerificationRepository otpRepository) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
    }

    /** Counts a wrong OTP guess against the issued code. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordOtpFailure(UUID otpVerificationId) {
        otpRepository
                .findById(otpVerificationId)
                .ifPresent(otp -> otp.setAttemptCount(otp.getAttemptCount() + 1));
    }

    /** Consumes an OTP, so that a later rollback cannot make a used code valid again. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markOtpVerified(UUID otpVerificationId) {
        otpRepository
                .findById(otpVerificationId)
                .ifPresent(otp -> otp.setVerifiedAt(Instant.now()));
    }

    /**
     * Counts a wrong password and locks the account once too many have accumulated.
     *
     * @return the running failure count, for the audit entry
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recordPasswordFailure(UUID userId) {
        return userRepository
                .findById(userId)
                .map(user -> {
                    int failures = user.getFailedLoginCount() + 1;
                    user.setFailedLoginCount(failures);
                    if (failures >= MAX_FAILED_LOGINS) {
                        user.setLockedUntil(Instant.now().plus(LOCKOUT));
                    }
                    return failures;
                })
                .orElse(0);
    }
}
