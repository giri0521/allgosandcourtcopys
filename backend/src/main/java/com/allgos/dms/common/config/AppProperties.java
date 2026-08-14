package com.allgos.dms.common.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Everything under the {@code app.*} prefix in application.yml.
 *
 * <p>Grouped as records so that configuration is immutable and typo-proof at startup rather than at
 * the first request that happens to read a property.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String timezone,
        Jwt jwt,
        Otp otp,
        Storage storage,
        Upload upload,
        Cors cors,
        RateLimit rateLimit) {

    /**
     * The per-address cap on the authentication endpoints.
     *
     * @param authRequestsPerMinute counted per network address, per instance — see
     *     {@code RateLimiter} for what that means behind a load balancer
     */
    public record RateLimit(boolean enabled, int authRequestsPerMinute) {}

    public record Jwt(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {}

    public record Otp(
            String provider,
            int length,
            Duration ttl,
            int maxAttempts,
            Duration resendCooldown,
            Msg91 msg91) {

        public record Msg91(String authKey, String templateId, String senderId) {}
    }

    public record Storage(
            String endpoint,
            String region,
            String bucket,
            String accessKey,
            String secretKey,
            boolean pathStyleAccess,
            Duration presignedUrlTtl) {}

    public record Upload(List<String> allowedContentTypes) {}

    public record Cors(List<String> allowedOrigins) {}
}
