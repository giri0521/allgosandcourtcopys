package com.allgos.dms.common.security;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A fixed-window request counter, keyed by whatever the caller decides identifies a client.
 *
 * <p>Fixed window rather than a sliding one, deliberately. A sliding window is smoother at the
 * boundary — a client can otherwise spend its whole allowance at 10:59:59 and again at 11:00:00 —
 * but it costs a timestamp list per key and the thing being defended against here is a script
 * making thousands of attempts, not somebody getting twice the budget once a minute. The simpler
 * structure is one integer per key and is easy to reason about at three in the morning.
 *
 * <p><b>Per instance, not per cluster.</b> Two application instances behind a load balancer each
 * enforce their own count, so the effective limit is the configured one times the number of
 * instances. That is fine for the deployment this is written for — one instance, ~150 users — and
 * would need Redis or the reverse proxy's own limiter if that changes. The deployment note in the
 * handoff says so.
 *
 * <p>Keys are evicted lazily as they are touched, plus a sweep when the map grows past a ceiling,
 * so a flood of distinct addresses cannot grow it without bound.
 */
public class RateLimiter {

    /** Above this many tracked keys, expired windows are swept before another is admitted. */
    private static final int SWEEP_THRESHOLD = 10_000;

    private final int permitsPerWindow;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(int permitsPerWindow, Duration window, Clock clock) {
        this.permitsPerWindow = permitsPerWindow;
        this.window = window;
        this.clock = clock;
    }

    /**
     * Counts one request against a key.
     *
     * @return true when the request is within the allowance, false when it should be refused
     */
    public boolean tryAcquire(String key) {
        if (windows.size() > SWEEP_THRESHOLD) {
            sweep();
        }

        long now = clock.millis();
        Window current = windows.compute(key, (ignored, existing) -> {
            if (existing == null || now - existing.startedAt >= window.toMillis()) {
                return new Window(now);
            }
            return existing;
        });

        return current.count.incrementAndGet() <= permitsPerWindow;
    }

    /** How long until this key's window resets, for the Retry-After header. */
    public Duration retryAfter(String key) {
        Window current = windows.get(key);
        if (current == null) {
            return Duration.ZERO;
        }
        long elapsed = clock.millis() - current.startedAt;
        return elapsed >= window.toMillis() ? Duration.ZERO : window.minusMillis(elapsed);
    }

    /** Forgets a key — used when an attempt succeeds and the count no longer means anything. */
    public void reset(String key) {
        windows.remove(key);
    }

    private void sweep() {
        long now = clock.millis();
        windows.values().removeIf(entry -> now - entry.startedAt >= window.toMillis());
    }

    /**
     * One key's current window. The count is atomic because two requests from the same address can
     * be handled by different threads at the same moment, and a lost increment is a free attempt.
     */
    private static final class Window {
        private final long startedAt;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
