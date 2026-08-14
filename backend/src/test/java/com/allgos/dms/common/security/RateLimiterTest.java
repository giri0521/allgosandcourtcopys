package com.allgos.dms.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The counter behind the authentication throttle.
 *
 * <p>A fake clock rather than sleeping: the window is a minute long, and a test suite that waited
 * for it would take a minute per assertion.
 */
class RateLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final Instant START = Instant.parse("2026-08-14T09:00:00Z");

    /** A clock the test moves by hand. */
    private static final class MovableClock extends Clock {
        private Instant now = START;

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        void advance(Duration amount) {
            now = now.plus(amount);
        }
    }

    @Test
    @DisplayName("permits exactly the allowance, then refuses")
    void allowsUpToTheLimit() {
        RateLimiter limiter = new RateLimiter(3, WINDOW, new MovableClock());

        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.1")).isFalse();
        assertThat(limiter.tryAcquire("10.0.0.1")).isFalse();
    }

    @Test
    @DisplayName("one caller's exhausted allowance does not affect another's")
    void countsPerKey() {
        RateLimiter limiter = new RateLimiter(1, WINDOW, new MovableClock());

        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.1")).isFalse();

        // A different address starts with its full allowance — otherwise one attacker would lock
        // out the whole office.
        assertThat(limiter.tryAcquire("10.0.0.2")).isTrue();
    }

    @Test
    @DisplayName("the allowance returns when the window rolls over")
    void resetsAfterTheWindow() {
        MovableClock clock = new MovableClock();
        RateLimiter limiter = new RateLimiter(2, WINDOW, clock);

        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.1")).isFalse();

        clock.advance(Duration.ofSeconds(59));
        assertThat(limiter.tryAcquire("10.0.0.1")).as("still inside the window").isFalse();

        clock.advance(Duration.ofSeconds(2));
        assertThat(limiter.tryAcquire("10.0.0.1")).as("the window has rolled over").isTrue();
    }

    @Test
    @DisplayName("retryAfter counts down and reaches zero when the window ends")
    void reportsWhenToRetry() {
        MovableClock clock = new MovableClock();
        RateLimiter limiter = new RateLimiter(1, WINDOW, clock);

        // Nothing recorded yet: there is nothing to wait for.
        assertThat(limiter.retryAfter("10.0.0.1")).isZero();

        limiter.tryAcquire("10.0.0.1");
        assertThat(limiter.retryAfter("10.0.0.1")).isEqualTo(WINDOW);

        clock.advance(Duration.ofSeconds(20));
        assertThat(limiter.retryAfter("10.0.0.1")).isEqualTo(Duration.ofSeconds(40));

        clock.advance(Duration.ofSeconds(41));
        assertThat(limiter.retryAfter("10.0.0.1")).isZero();
    }

    @Test
    void resetForgetsAKey() {
        RateLimiter limiter = new RateLimiter(1, WINDOW, new MovableClock());

        limiter.tryAcquire("10.0.0.1");
        assertThat(limiter.tryAcquire("10.0.0.1")).isFalse();

        limiter.reset("10.0.0.1");
        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
    }

    @Test
    @DisplayName("concurrent requests from one caller cannot exceed the allowance")
    void doesNotLoseIncrementsUnderContention() throws Exception {
        int permits = 50;
        int threads = 32;
        int attemptsPerThread = 10;

        RateLimiter limiter = new RateLimiter(permits, WINDOW, new MovableClock());
        AtomicInteger allowed = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<?> futures = IntStream.range(0, threads)
                    .mapToObj(ignored -> pool.submit(() -> {
                        start.await();
                        for (int attempt = 0; attempt < attemptsPerThread; attempt++) {
                            if (limiter.tryAcquire("10.0.0.1")) {
                                allowed.incrementAndGet();
                            }
                        }
                        return null;
                    }))
                    .toList();

            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            assertThat(futures).hasSize(threads);
        }

        // A lost increment is a free attempt for an attacker, so this must be exact rather than
        // approximately right.
        assertThat(allowed.get()).isEqualTo(permits);
    }
}
