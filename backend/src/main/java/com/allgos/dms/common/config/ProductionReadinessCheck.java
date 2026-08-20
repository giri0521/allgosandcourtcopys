package com.allgos.dms.common.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to start with the development defaults still in place.
 *
 * <p>Every one of these is a value that works perfectly in development and is a serious hole in
 * production, which is exactly the combination that survives a deployment. Nothing here would ever
 * be caught by a test or noticed in a screen: a placeholder signing secret issues tokens that verify
 * correctly, and MinIO's default credentials let the application read and write documents happily.
 * The failure is silent by nature, so the check has to be explicit.
 *
 * <p>It runs only when the {@code prod} profile is active. In development these defaults are the
 * point — a new contributor should be able to clone the repository and run it — so the check must
 * not make that harder.
 *
 * <p>Failing at start-up rather than warning is deliberate. A warning in a log nobody reads is the
 * same as no check at all, and an instance that refuses to start is a problem someone fixes in
 * minutes rather than a hole nobody notices for a year.
 */
@Component
public class ProductionReadinessCheck implements ApplicationListener<ApplicationReadyEvent> {

    /** The values shipped in application.yml. Matching one in production means it was never set. */
    private static final String DEFAULT_JWT_SECRET =
            "change-me-in-every-environment-this-must-be-at-least-32-bytes";
    private static final String DEFAULT_STORAGE_KEY = "minioadmin";
    private static final String DEFAULT_ADMIN_MOBILE = "9999999999";

    private final AppProperties properties;
    private final Environment environment;

    public ProductionReadinessCheck(AppProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!environment.matchesProfiles("prod")) {
            return;
        }

        List<String> problems = new ArrayList<>();

        if (DEFAULT_JWT_SECRET.equals(properties.jwt().secret())) {
            // The worst of the three: anyone who has read this repository can mint an admin token.
            problems.add("app.jwt.secret is still the placeholder from application.yml — set JWT_SECRET");
        }

        if (DEFAULT_STORAGE_KEY.equals(properties.storage().accessKey())
                || DEFAULT_STORAGE_KEY.equals(properties.storage().secretKey())) {
            problems.add("app.storage credentials are still MinIO's defaults — set S3_ACCESS_KEY and S3_SECRET_KEY");
        }

        if (properties.cors().allowedOrigins().stream().anyMatch(origin -> origin.contains("localhost"))) {
            problems.add("app.cors.allowed-origins still contains localhost — set CORS_ORIGINS");
        }

        if (!properties.rateLimit().enabled()) {
            problems.add("app.rate-limit.enabled is false — the authentication endpoints are unthrottled");
        }

        if ("mock".equals(properties.otp().provider())) {
            // The mock provider writes the code to the log instead of sending it, so anyone who can
            // read the logs can complete a password reset on any account.
            problems.add("app.otp.provider is 'mock' — no OTP is actually sent; set OTP_PROVIDER");
        }

        if (DEFAULT_ADMIN_MOBILE.equals(environment.getProperty("app.seed.admin-mobile", DEFAULT_ADMIN_MOBILE))) {
            problems.add("app.seed.admin-mobile is still 9999999999 — set SEED_ADMIN_MOBILE");
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Refusing to run in production with development defaults:%n  - %s"
                            .formatted(String.join("%n  - ".formatted(), problems)));
        }
    }
}
