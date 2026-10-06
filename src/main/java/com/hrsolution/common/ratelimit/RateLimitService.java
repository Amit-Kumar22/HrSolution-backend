package com.hrsolution.common.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hrsolution.common.config.AuthProperties;
import com.hrsolution.common.error.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Token-bucket rate limiting, backed by Bucket4j with an in-memory Caffeine
 * store.
 *
 * <p>Buckets are per key, where a key names both the limit and the subject -
 * {@code "login:ip:203.0.113.4"}, {@code "login:email:a@b.com"}. Keeping those
 * separate is the point: an office behind one NAT address shares an IP, so a
 * per-IP limit alone would lock out a whole office the moment one person
 * fumbled their password, while a per-email limit alone would let an attacker
 * spray one attempt each across thousands of accounts.
 *
 * <p><strong>In-memory, therefore per instance.</strong> With two application
 * instances the effective limit doubles. That is an accepted trade for now;
 * Bucket4j's distributed backends (Redis, Hazelcast) are a drop-in replacement
 * for {@link #buckets} when horizontal scaling arrives.
 */
@Slf4j
@Service
public class RateLimitService {

    /**
     * Bounded so a flood of distinct keys cannot exhaust the heap - which would
     * turn a rate-limiting feature into a denial-of-service vector. Idle
     * entries expire, since a bucket is only interesting while being consumed.
     */
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(2, TimeUnit.HOURS)
            .build();

    /**
     * Consumes one token, or throws.
     *
     * @throws RateLimitExceededException when the bucket is empty, carrying the
     *                                    seconds until a token is available
     */
    public void consumeOrThrow(String key, AuthProperties.RateLimit.Rule rule) {
        ConsumptionProbe probe = tryConsume(key, rule);
        if (!probe.isConsumed()) {
            long retryAfterSeconds = Math.max(1L,
                    Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
            // Debug, not warn: a tripped limit is routine on a public endpoint.
            // The security-relevant signal is the audit event the caller writes.
            log.debug("Rate limit hit for key '{}', retry in {}s", key, retryAfterSeconds);
            throw RateLimitExceededException.of(retryAfterSeconds);
        }
    }

    public ConsumptionProbe tryConsume(String key, AuthProperties.RateLimit.Rule rule) {
        Bucket bucket = buckets.get(key, ignored -> newBucket(rule));
        return bucket.tryConsumeAndReturnRemaining(1);
    }

    /** Clears all buckets. For tests, so one test's attempts cannot fail another's. */
    public void reset() {
        buckets.invalidateAll();
    }

    private Bucket newBucket(AuthProperties.RateLimit.Rule rule) {
        // refillGreedy tops the bucket up smoothly across the period rather
        // than all at once at the boundary, so a caller who waits a moment gets
        // a token back instead of having to wait out the full window.
        Bandwidth limit = Bandwidth.builder()
                .capacity(rule.getCapacity())
                .refillGreedy(rule.getCapacity(), rule.getPeriod())
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
