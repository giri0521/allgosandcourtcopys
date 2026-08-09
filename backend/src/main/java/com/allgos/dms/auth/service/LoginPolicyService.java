package com.allgos.dms.auth.service;

import com.allgos.dms.common.config.AppProperties;
import com.allgos.dms.common.exception.ApiException;
import com.allgos.dms.user.entity.User;
import com.allgos.dms.user.entity.UserStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;

/**
 * The two rules that decide whether a sign-in attempt may proceed.
 *
 * <p><b>1. Approval.</b> Only an ACTIVE account can obtain a token, by either login path. A pending,
 * rejected or disabled account is refused here, on the server — the client is never trusted to hide
 * a screen from someone who has not been approved.
 *
 * <p><b>2. Daily OTP.</b> OTP is mandatory on the first sign-in of each calendar day. Once that
 * succeeds, {@code lastOtpLoginDate} is stamped with today and password sign-in is accepted for the
 * remainder of the day. The date always comes from the server clock in the configured zone; nothing
 * the client sends can influence it, so no request can skip the OTP.
 */
@Service
public class LoginPolicyService {

    private final Clock clock;

    /**
     * @param clock injected so tests can roll the date forward and assert that the OTP requirement
     *     comes back, rather than depending on the wall clock
     */
    public LoginPolicyService(AppProperties properties, Clock clock) {
        this.clock = clock.withZone(ZoneId.of(properties.timezone()));
    }

    /** Today, in the application's configured timezone. */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /**
     * Rejects any account that is not approved and active.
     *
     * <p>Each state gets its own message so the login screen can explain what happened, but all of
     * them refuse equally.
     */
    public void assertCanSignIn(User user) {
        UserStatus status = user.getStatus();
        if (status.canSignIn()) {
            if (user.isLocked()) {
                throw ApiException.forbidden(
                        "ACCOUNT_LOCKED", "Too many failed attempts. Please try again later.");
            }
            return;
        }

        throw switch (status) {
            case PENDING -> ApiException.forbidden(
                    "ACCOUNT_PENDING", "Your registration is awaiting administrator approval");
            case REJECTED -> ApiException.forbidden(
                    "ACCOUNT_REJECTED", "Your registration was not approved. Contact your administrator.");
            case INACTIVE -> ApiException.forbidden(
                    "ACCOUNT_INACTIVE", "Your account has been disabled. Contact your administrator.");
            case ACTIVE -> throw new IllegalStateException("unreachable");
        };
    }

    /** True when this user has already completed an OTP sign-in today. */
    public boolean hasVerifiedOtpToday(User user) {
        return today().equals(user.getLastOtpLoginDate());
    }

    /**
     * Gate for the password path. The first sign-in of the day must go through OTP; the web app
     * reacts to this specific code by switching to the OTP tab.
     */
    public void assertPasswordLoginAllowed(User user) {
        if (!hasVerifiedOtpToday(user)) {
            throw ApiException.forbidden(
                    "OTP_REQUIRED_TODAY", "For security, verify with an OTP once each day");
        }
    }

    /** Called after a successful OTP sign-in; opens the password path until midnight. */
    public void recordOtpLogin(User user) {
        user.setLastOtpLoginDate(today());
    }
}
