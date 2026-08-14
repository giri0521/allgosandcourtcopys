package com.allgos.dms.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.allgos.dms.common.config.AppProperties;
import com.allgos.dms.common.exception.ApiException;
import com.allgos.dms.user.entity.User;
import com.allgos.dms.user.entity.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The two rules that decide whether anyone gets in. Both are exercised against a fixed clock so the
 * date rollover can be asserted without waiting for midnight.
 */
class LoginPolicyServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /** 2026-08-09 20:00 UTC — which is already 2026-08-10 in IST, so the zone genuinely matters. */
    private static final Instant EVENING_UTC = Instant.parse("2026-08-09T20:00:00Z");

    private static LoginPolicyService policyAt(Instant instant) {
        AppProperties properties = new AppProperties("Asia/Kolkata", null, null, null, null, null, null);
        return new LoginPolicyService(properties, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static User activeUser() {
        User user = new User();
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    @Nested
    @DisplayName("approval gate")
    class ApprovalGate {

        @Test
        void allowsAnActiveAccount() {
            assertThatCode(() -> policyAt(EVENING_UTC).assertCanSignIn(activeUser()))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest
        @EnumSource(value = UserStatus.class, names = {"PENDING", "REJECTED", "INACTIVE"})
        void refusesEveryUnapprovedStatus(UserStatus status) {
            User user = new User();
            user.setStatus(status);

            assertThatThrownBy(() -> policyAt(EVENING_UTC).assertCanSignIn(user))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getCode())
                    .isEqualTo("ACCOUNT_" + status.name());
        }

        @Test
        void refusesAnActiveButLockedAccount() {
            User user = activeUser();
            user.setLockedUntil(Instant.now().plusSeconds(600));

            assertThatThrownBy(() -> policyAt(EVENING_UTC).assertCanSignIn(user))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getCode())
                    .isEqualTo("ACCOUNT_LOCKED");
        }
    }

    @Nested
    @DisplayName("daily OTP rule")
    class DailyOtp {

        @Test
        void resolvesTodayInTheConfiguredZoneNotUtc() {
            // 20:00 UTC on the 9th is 01:30 on the 10th in IST.
            assertThat(policyAt(EVENING_UTC).today()).isEqualTo(LocalDate.of(2026, 8, 10));
        }

        @Test
        void requiresOtpWhenTheUserHasNeverSignedIn() {
            assertThatThrownBy(() -> policyAt(EVENING_UTC).assertPasswordLoginAllowed(activeUser()))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getCode())
                    .isEqualTo("OTP_REQUIRED_TODAY");
        }

        @Test
        void allowsPasswordLoginOnceAnOtpHasSucceededToday() {
            LoginPolicyService policy = policyAt(EVENING_UTC);
            User user = activeUser();

            policy.recordOtpLogin(user);

            assertThat(user.getLastOtpLoginDate()).isEqualTo(LocalDate.of(2026, 8, 10));
            assertThatCode(() -> policy.assertPasswordLoginAllowed(user)).doesNotThrowAnyException();
        }

        @Test
        void requiresOtpAgainAfterTheDateRollsOver() {
            User user = activeUser();
            policyAt(EVENING_UTC).recordOtpLogin(user);

            // Same user, one day later: the stamp is now stale.
            LoginPolicyService tomorrow = policyAt(EVENING_UTC.plus(java.time.Duration.ofDays(1)));

            assertThat(tomorrow.hasVerifiedOtpToday(user)).isFalse();
            assertThatThrownBy(() -> tomorrow.assertPasswordLoginAllowed(user))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getCode())
                    .isEqualTo("OTP_REQUIRED_TODAY");
        }

        @Test
        void aLateEveningIstLoginStillCountsForThatIstDay() {
            // 18:00 IST on 2026-08-10 == 12:30 UTC.
            Instant istEvening = Instant.parse("2026-08-10T12:30:00Z");
            User user = activeUser();
            policyAt(istEvening).recordOtpLogin(user);

            assertThat(user.getLastOtpLoginDate())
                    .isEqualTo(LocalDate.ofInstant(istEvening, IST));
            assertThatCode(() -> policyAt(istEvening).assertPasswordLoginAllowed(user))
                    .doesNotThrowAnyException();
        }
    }
}
