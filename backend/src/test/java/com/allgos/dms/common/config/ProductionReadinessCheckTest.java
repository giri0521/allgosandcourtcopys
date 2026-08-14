package com.allgos.dms.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * The check that stops a deployment running on development defaults.
 *
 * <p>Each of these values works perfectly — that is the whole problem. A placeholder signing secret
 * issues tokens that verify, MinIO's default credentials read and write documents happily, and the
 * mock OTP provider lets everyone in. Nothing fails, nothing is logged, and the system is wide open.
 */
class ProductionReadinessCheckTest {

    private static final String DEFAULT_SECRET =
            "change-me-in-every-environment-this-must-be-at-least-32-bytes";
    private static final String REAL_SECRET = "a-genuinely-random-production-secret-of-sufficient-length";

    @Test
    @DisplayName("outside production the defaults are the point, and are left alone")
    void doesNothingOutsideProduction() {
        MockEnvironment development = new MockEnvironment().withProperty("spring.profiles.active", "dev");
        development.setActiveProfiles("dev");

        assertThatCode(() -> check(everythingDefault(), development).onApplicationEvent(null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a fully configured production instance starts")
    void allowsAProperlyConfiguredProduction() {
        assertThatCode(() -> check(fullyConfigured(), production()).onApplicationEvent(null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the placeholder signing secret stops the instance")
    void refusesThePlaceholderSecret() {
        AppProperties properties = properties(
                DEFAULT_SECRET, "real-key", "real-secret", List.of("https://dms.example.gov.in"), true, "msg91");

        assertThatThrownBy(() -> check(properties, production()).onApplicationEvent(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.jwt.secret");
    }

    @Test
    @DisplayName("MinIO's default credentials stop the instance")
    void refusesDefaultStorageCredentials() {
        AppProperties properties = properties(
                REAL_SECRET, "minioadmin", "minioadmin", List.of("https://dms.example.gov.in"), true, "msg91");

        assertThatThrownBy(() -> check(properties, production()).onApplicationEvent(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.storage credentials");
    }

    @Test
    @DisplayName("a localhost CORS origin stops the instance")
    void refusesLocalhostCors() {
        AppProperties properties = properties(
                REAL_SECRET, "real-key", "real-secret", List.of("http://localhost:5173"), true, "msg91");

        assertThatThrownBy(() -> check(properties, production()).onApplicationEvent(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.cors.allowed-origins");
    }

    @Test
    @DisplayName("the mock OTP provider stops the instance — nothing would actually be sent")
    void refusesTheMockOtpProvider() {
        AppProperties properties = properties(
                REAL_SECRET, "real-key", "real-secret", List.of("https://dms.example.gov.in"), true, "mock");

        assertThatThrownBy(() -> check(properties, production()).onApplicationEvent(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.otp.provider");
    }

    @Test
    @DisplayName("a disabled rate limiter stops the instance")
    void refusesAnUnthrottledAuthSurface() {
        AppProperties properties = properties(
                REAL_SECRET, "real-key", "real-secret", List.of("https://dms.example.gov.in"), false, "msg91");

        assertThatThrownBy(() -> check(properties, production()).onApplicationEvent(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.rate-limit.enabled");
    }

    @Test
    @DisplayName("every problem is reported at once, not one per restart")
    void reportsEveryProblemTogether() {
        assertThatThrownBy(() -> check(everythingDefault(), production()).onApplicationEvent(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.jwt.secret")
                .hasMessageContaining("app.storage credentials")
                .hasMessageContaining("app.cors.allowed-origins")
                .hasMessageContaining("app.otp.provider");
    }

    // ------------------------------------------------------------------------ helpers

    private static ProductionReadinessCheck check(AppProperties properties, MockEnvironment environment) {
        return new ProductionReadinessCheck(properties, environment);
    }

    private static MockEnvironment production() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        // A configured deployment overrides the seeded admin's number too.
        environment.withProperty("app.seed.admin-mobile", "9876500000");
        return environment;
    }

    private static AppProperties everythingDefault() {
        return properties(
                DEFAULT_SECRET, "minioadmin", "minioadmin", List.of("http://localhost:5173"), true, "mock");
    }

    private static AppProperties fullyConfigured() {
        return properties(
                REAL_SECRET, "real-key", "real-secret", List.of("https://dms.example.gov.in"), true, "msg91");
    }

    private static AppProperties properties(
            String jwtSecret,
            String storageAccessKey,
            String storageSecretKey,
            List<String> corsOrigins,
            boolean rateLimitEnabled,
            String otpProvider) {

        return new AppProperties(
                "Asia/Kolkata",
                new AppProperties.Jwt(jwtSecret, Duration.ofMinutes(15), Duration.ofDays(7)),
                new AppProperties.Otp(otpProvider, 6, Duration.ofMinutes(5), 5, Duration.ofSeconds(45), null),
                new AppProperties.Storage(
                        "https://s3.example", "ap-south-1", "bucket", storageAccessKey, storageSecretKey,
                        false, Duration.ofMinutes(5)),
                new AppProperties.Upload(List.of("application/pdf")),
                new AppProperties.Cors(corsOrigins),
                new AppProperties.RateLimit(rateLimitEnabled, 60));
    }
}
