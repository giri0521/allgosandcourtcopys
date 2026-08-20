package com.allgos.dms.common.config;

import com.allgos.dms.user.entity.User;
import com.allgos.dms.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Gives the seeded first admin a known password, on a development machine only.
 *
 * <p>V3 seeds that account with no password at all, deliberately: in a real deployment the operator
 * claims it through the forgotten-password reset, so no default credential ever exists. That is
 * correct and stays correct — but it also means every {@code docker compose down -v} costs a reset
 * round trip before anyone can sign in at all, which is a poor first five minutes for someone who
 * has just cloned the repository.
 *
 * <p><b>Three conditions must all hold, and each one alone is enough to stop it.</b> The bean exists
 * only under the {@code default} profile, so {@code prod}, {@code test} and any named profile never
 * load it. Beyond that it refuses unless the seeded mobile number and the JWT secret are both still
 * the placeholders shipped in application.yml — the combination is unmistakably a development
 * machine, and it closes the one hole the profile check leaves open: a production deployment where
 * somebody forgot to set {@code SPRING_PROFILES_ACTIVE=prod}.
 *
 * <p>It never overwrites an existing password. Once anyone has set a real one — through the reset,
 * or from the profile screen — this becomes a no-op, so a restart cannot hand the account back to
 * whoever knows the default.
 */
@Component
@Profile("default")
public class DevAdminPasswordSeeder implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(DevAdminPasswordSeeder.class);

    /** The values shipped in application.yml. Both still matching means nobody has configured this. */
    private static final String DEFAULT_ADMIN_MOBILE = "9999999999";

    private static final String DEFAULT_JWT_SECRET =
            "change-me-in-every-environment-this-must-be-at-least-32-bytes";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;
    private final Environment environment;

    public DevAdminPasswordSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AppProperties properties,
            Environment environment) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        String mobile = environment.getProperty("app.seed.admin-mobile", DEFAULT_ADMIN_MOBILE);

        if (!DEFAULT_ADMIN_MOBILE.equals(mobile)
                || !DEFAULT_JWT_SECRET.equals(properties.jwt().secret())) {
            // Something here has been configured for a real environment. Whatever this is, it is
            // not a scratch database, and it does not get a default credential.
            return;
        }

        String password = environment.getProperty("app.seed.admin-password");
        if (password == null || password.isBlank()) {
            return;
        }

        userRepository.findByMobileNumber(mobile).ifPresent(admin -> {
            if (admin.getPasswordHash() != null) {
                return;
            }

            admin.setPasswordHash(passwordEncoder.encode(password));
            admin.setFailedLoginCount(0);
            admin.setLockedUntil(null);
            userRepository.save(admin);

            // Loud, and it prints the password: it is a known constant in a public repository, so
            // there is nothing to protect, and someone running this for the first time needs to be
            // told what to type.
            log.warn(
                    "=== DEV ADMIN === {} can now sign in with password '{}' - development only; "
                            + "never set app.seed.admin-password in a real deployment",
                    mobile,
                    password);
        });
    }
}
