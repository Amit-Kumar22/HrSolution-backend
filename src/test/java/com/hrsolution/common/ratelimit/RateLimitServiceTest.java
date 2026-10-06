package com.hrsolution.common.ratelimit;

import com.hrsolution.common.config.AuthProperties;
import com.hrsolution.common.error.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Rate limiting, tested without a clock or a Spring context - the bucket is
 * exhausted by consuming it rather than by waiting.
 */
class RateLimitServiceTest {

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService();
    }

    private AuthProperties.RateLimit.Rule rule(long capacity) {
        return new AuthProperties.RateLimit.Rule(capacity, Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("allows requests up to the capacity, then rejects")
    void allowsUpToCapacity() {
        AuthProperties.RateLimit.Rule threePerMinute = rule(3);

        for (int attempt = 1; attempt <= 3; attempt++) {
            int finalAttempt = attempt;
            assertThatCode(() -> rateLimitService.consumeOrThrow("key", threePerMinute))
                    .as("attempt %d should be allowed", finalAttempt)
                    .doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> rateLimitService.consumeOrThrow("key", threePerMinute))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    @DisplayName("reports a positive Retry-After, never zero")
    void reportsRetryAfter() {
        AuthProperties.RateLimit.Rule onePerMinute = rule(1);
        rateLimitService.consumeOrThrow("key", onePerMinute);

        assertThatThrownBy(() -> rateLimitService.consumeOrThrow("key", onePerMinute))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(thrown -> {
                    long retryAfter = ((RateLimitExceededException) thrown).getRetryAfterSeconds();
                    // Floored at 1: a Retry-After of 0 tells a client to retry
                    // immediately, which would just be rejected again.
                    assertThat(retryAfter).isGreaterThanOrEqualTo(1);
                    assertThat(retryAfter).isLessThanOrEqualTo(60);
                });
    }

    @Test
    @DisplayName("buckets are independent per key")
    void keysAreIndependent() {
        AuthProperties.RateLimit.Rule onePerMinute = rule(1);

        rateLimitService.consumeOrThrow("ip:203.0.113.1", onePerMinute);

        // Exhausting one caller's bucket must not affect anyone else - this is
        // what stops one noisy IP locking out the whole endpoint.
        assertThatCode(() -> rateLimitService.consumeOrThrow("ip:203.0.113.2", onePerMinute))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> rateLimitService.consumeOrThrow("ip:203.0.113.1", onePerMinute))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    @DisplayName("the per-email and per-IP limits for one login do not share a bucket")
    void emailAndIpLimitsAreSeparate() {
        AuthProperties.RateLimit.Rule onePerMinute = rule(1);

        rateLimitService.consumeOrThrow("ip:/api/v1/auth/login:203.0.113.1", onePerMinute);

        // Namespacing matters: if these collided, one failed sign-in would
        // consume both budgets at once.
        assertThatCode(() ->
                rateLimitService.consumeOrThrow("login:email:asha@example.com", onePerMinute))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("reset clears every bucket")
    void resetClearsBuckets() {
        AuthProperties.RateLimit.Rule onePerMinute = rule(1);
        rateLimitService.consumeOrThrow("key", onePerMinute);

        rateLimitService.reset();

        assertThatCode(() -> rateLimitService.consumeOrThrow("key", onePerMinute))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("tryConsume reports the remaining budget without throwing")
    void tryConsumeReportsRemaining() {
        AuthProperties.RateLimit.Rule twoPerMinute = rule(2);

        assertThat(rateLimitService.tryConsume("key", twoPerMinute).isConsumed()).isTrue();
        assertThat(rateLimitService.tryConsume("key", twoPerMinute).getRemainingTokens()).isZero();
        assertThat(rateLimitService.tryConsume("key", twoPerMinute).isConsumed()).isFalse();
    }
}
