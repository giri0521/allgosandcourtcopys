package com.allgos.dms.common.security;

import com.allgos.dms.common.config.AppProperties;
import com.allgos.dms.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and reads the access and refresh tokens.
 *
 * <p>Both carry the user's {@code tokenVersion}. "Log out from all devices" increments that column,
 * which instantly invalidates every token already in circulation without needing a revocation list.
 */
@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_VERSION = "tv";
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final AppProperties.Jwt config;
    private final Clock clock;

    public JwtService(AppProperties properties, Clock clock) {
        this.config = properties.jwt();
        this.clock = clock;

        byte[] secret = config.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret);
    }

    public String issueAccessToken(User user) {
        return issue(user, TYPE_ACCESS, config.accessTokenTtl().toMillis());
    }

    public String issueRefreshToken(User user) {
        return issue(user, TYPE_REFRESH, config.refreshTokenTtl().toMillis());
    }

    private String issue(User user, String type, long ttlMillis) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_TOKEN_VERSION, user.getTokenVersion())
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(ttlMillis)))
                .signWith(key)
                .compact();
    }

    /** @throws JwtException if the token is malformed, expired or not signed by this server */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public UUID userIdOf(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public int tokenVersionOf(Claims claims) {
        return claims.get(CLAIM_TOKEN_VERSION, Integer.class);
    }

    public boolean isAccessToken(Claims claims) {
        return TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public Duration accessTokenTtl() {
        return config.accessTokenTtl();
    }

    public Duration refreshTokenTtl() {
        return config.refreshTokenTtl();
    }
}
