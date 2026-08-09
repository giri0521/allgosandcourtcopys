package com.allgos.dms.common.security;

import com.allgos.dms.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Turns a bearer access token into an authenticated principal.
 *
 * <p>The user row is re-read on every request rather than trusted from the token, so that an admin
 * disabling an account, or a "log out from all devices", takes effect on the very next call instead
 * of when the access token happens to expire.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        resolveToken(request).flatMap(this::authenticate).ifPresent(authenticated -> {
            var authentication = new UsernamePasswordAuthenticationToken(
                    authenticated, null, authenticated.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        });

        filterChain.doFilter(request, response);
    }

    private Optional<AuthenticatedUser> authenticate(String token) {
        Claims claims;
        try {
            claims = jwtService.parse(token);
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty(); // expired, tampered with, or not ours
        }

        if (!jwtService.isAccessToken(claims)) {
            return Optional.empty(); // a refresh token must never authenticate a request
        }

        return userRepository
                .findById(jwtService.userIdOf(claims))
                .filter(user -> user.getStatus().canSignIn())
                .filter(user -> user.getTokenVersion() == jwtService.tokenVersionOf(claims))
                .filter(user -> !user.isLocked())
                .map(AuthenticatedUser::new);
    }

    private Optional<String> resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return Optional.of(header.substring(7));
        }
        return Optional.empty();
    }
}
