package com.allgos.dms.auth.service;

import com.allgos.dms.common.exception.ApiException;
import com.allgos.dms.user.entity.User;
import com.allgos.dms.user.entity.UserStatus;
import org.springframework.stereotype.Service;

/**
 * The rule that decides whether a sign-in attempt may proceed.
 *
 * <p><b>Approval.</b> Only an ACTIVE, unlocked account can obtain a token. A pending, rejected or
 * disabled account is refused here, on the server — the client is never trusted to hide a screen
 * from someone who has not been approved.
 */
@Service
public class LoginPolicyService {

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
}
