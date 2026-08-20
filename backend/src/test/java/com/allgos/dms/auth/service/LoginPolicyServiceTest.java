package com.allgos.dms.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.allgos.dms.common.exception.ApiException;
import com.allgos.dms.user.entity.User;
import com.allgos.dms.user.entity.UserStatus;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The rule that decides whether anyone gets in: approved, active, and not locked out. */
@DisplayName("approval gate")
class LoginPolicyServiceTest {

    private final LoginPolicyService policy = new LoginPolicyService();

    private static User activeUser() {
        User user = new User();
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    @Test
    void allowsAnActiveAccount() {
        assertThatCode(() -> policy.assertCanSignIn(activeUser())).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"PENDING", "REJECTED", "INACTIVE"})
    void refusesEveryUnapprovedStatus(UserStatus status) {
        User user = new User();
        user.setStatus(status);

        assertThatThrownBy(() -> policy.assertCanSignIn(user))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("ACCOUNT_" + status.name());
    }

    @Test
    void refusesAnActiveButLockedAccount() {
        User user = activeUser();
        user.setLockedUntil(Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> policy.assertCanSignIn(user))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("ACCOUNT_LOCKED");
    }

    @Test
    void allowsAnAccountWhoseLockHasExpired() {
        User user = activeUser();
        user.setLockedUntil(Instant.now().minusSeconds(1));

        assertThatCode(() -> policy.assertCanSignIn(user)).doesNotThrowAnyException();
    }
}
