package com.allgos.dms.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The caller's address, as far as it can be known.
 *
 * <p>Behind the government/NIC reverse proxy the socket address is the proxy's, so the real client
 * arrives in {@code X-Forwarded-For} and the first entry of that list is the one to keep. The header
 * is client-controllable and therefore evidence rather than proof — it is recorded, never trusted
 * for a decision.
 *
 * <p>Shared by the audit trail and the download history, which record the same address for the same
 * request and must not disagree about how it was derived.
 */
public final class ClientIp {

    /** @return the caller's address, or null outside a request (a scheduled job, a test) */
    public static String current() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private ClientIp() {}
}
