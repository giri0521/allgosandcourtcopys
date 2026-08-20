package com.allgos.dms.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.allgos.dms.user.entity.User;
import com.allgos.dms.user.entity.UserRole;
import com.allgos.dms.user.entity.UserStatus;
import com.allgos.dms.user.repository.UserRepository;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The guards on the development-only admin password.
 *
 * <p>What is being proved is mostly what this does *not* do. A default credential that leaks into a
 * real environment is the kind of mistake that is found by someone else, so each condition that
 * stops it is asserted on its own rather than trusted to the profile annotation.
 */
class DevAdminPasswordSeederTest {

    private static final String DEFAULT_MOBILE = "9999999999";
    private static final String DEFAULT_SECRET =
            "change-me-in-every-environment-this-must-be-at-least-32-bytes";
    private static final String REAL_SECRET = "a-genuinely-random-production-secret-of-sufficient-length";
    private static final String PASSWORD = "Admin@12345";

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private UserRepository userRepository;
    private User admin;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);

        admin = new User();
        admin.setFullName("System Administrator");
        admin.setMobileNumber(DEFAULT_MOBILE);
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByMobileNumber(DEFAULT_MOBILE)).thenReturn(Optional.of(admin));
    }

    private void run(String mobile, String jwtSecret, String seededPassword) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("app.seed.admin-mobile", mobile);
        if (seededPassword != null) {
            environment.setProperty("app.seed.admin-password", seededPassword);
        }

        new DevAdminPasswordSeeder(userRepository, passwordEncoder, properties(jwtSecret), environment)
                .onApplicationEvent(mock(ApplicationReadyEvent.class));
    }

    private static AppProperties properties(String jwtSecret) {
        return new AppProperties(
                "Asia/Kolkata",
                new AppProperties.Jwt(jwtSecret, Duration.ofMinutes(15), Duration.ofDays(7)),
                null,
                null,
                null,
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.RateLimit(true, 60));
    }

    @Test
    @DisplayName("on a development machine the seeded admin gets the known password")
    void seedsTheDefaultAdmin() {
        run(DEFAULT_MOBILE, DEFAULT_SECRET, PASSWORD);

        assertThat(admin.getPasswordHash()).isNotNull();
        assertThat(passwordEncoder.matches(PASSWORD, admin.getPasswordHash())).isTrue();
        verify(userRepository).save(admin);
    }

    @Test
    @DisplayName("a configured JWT secret stops it — that is not a scratch database")
    void refusesWhenTheJwtSecretIsReal() {
        run(DEFAULT_MOBILE, REAL_SECRET, PASSWORD);

        assertThat(admin.getPasswordHash()).isNull();
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("a configured admin mobile stops it")
    void refusesWhenTheAdminMobileIsConfigured() {
        run("9812345678", DEFAULT_SECRET, PASSWORD);

        assertThat(admin.getPasswordHash()).isNull();
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("blanking the property turns it off entirely")
    void refusesWhenNoPasswordIsConfigured() {
        run(DEFAULT_MOBILE, DEFAULT_SECRET, "");

        assertThat(admin.getPasswordHash()).isNull();
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("an existing password is never overwritten, however often the app restarts")
    void neverOverwritesAPasswordSomebodyHasSet() {
        admin.setPasswordHash(passwordEncoder.encode("the-real-one"));
        String before = admin.getPasswordHash();

        run(DEFAULT_MOBILE, DEFAULT_SECRET, PASSWORD);

        assertThat(admin.getPasswordHash()).isEqualTo(before);
        verify(userRepository, never()).save(any());
    }
}
