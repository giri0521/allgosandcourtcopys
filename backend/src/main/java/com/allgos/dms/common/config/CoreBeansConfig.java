package com.allgos.dms.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class CoreBeansConfig {

    /**
     * Injected everywhere a date or time is needed rather than calling Instant.now() inline, so the
     * daily-OTP rule can be tested across a date rollover without waiting for midnight.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Hashes both passwords and OTP codes. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
