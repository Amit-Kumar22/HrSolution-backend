package com.hrsolution.common.ratelimit;

import com.hrsolution.common.config.AuthProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which paths {@link RateLimitFilter} guards, and how it keys its buckets.
 *
 * <p>The filter matched literal paths until Phase 5 added
 * {@code POST /api/v1/public/jobs/{slug}/apply}, which carries a variable. The
 * two things that can go wrong with the pattern matching it gained are both
 * silent:
 *
 * <ul>
 *   <li>the pattern not matching at all, leaving the most abusable endpoint in
 *       the system - anonymous, world-reachable, accepts a file upload -
 *       completely unlimited;</li>
 *   <li>the bucket being keyed on the concrete path rather than the pattern,
 *       which would give every job slug its own allowance and let an attacker
 *       multiply their budget by the number of open vacancies.</li>
 * </ul>
 *
 * <p>Neither shows up in a functional test, because both still return 201.
 */
class RateLimitFilterTest {

    private static final String APPLY_GUARD =
            "/api/v1/public/jobs/security-guard-pune/apply";
    private static final String APPLY_HELPER =
            "/api/v1/public/jobs/helper-nashik/apply";

    private AuthProperties authProperties;
    private RateLimitService rateLimitService;
    private RateLimitFilter filter;
    private FilterChain chain;
    private HandlerExceptionResolver resolver;

    @BeforeEach
    void setUp() {
        authProperties = new AuthProperties();
        // Two requests per window, so the third is the one that must be refused.
        authProperties.getRateLimit().getPublicForm().setCapacity(2);
        authProperties.getRateLimit().getPublicForm().setPeriod(Duration.ofHours(1));

        rateLimitService = new RateLimitService();
        chain = Mockito.mock(FilterChain.class);
        resolver = Mockito.mock(HandlerExceptionResolver.class);
        filter = new RateLimitFilter(authProperties, rateLimitService, resolver);
    }

    @Nested
    @DisplayName("path matching")
    class PathMatching {

        @Test
        @DisplayName("the apply endpoint is guarded despite its variable segment")
        void variablePathIsGuarded() throws Exception {
            // Would have been missed entirely by the literal-path map this
            // filter used before Phase 5.
            assertThat(passesThrough(post(APPLY_GUARD))).isTrue();
            assertThat(passesThrough(post(APPLY_GUARD))).isTrue();

            // Third request: refused, and the chain is not continued because
            // the response has already been written.
            assertThat(passesThrough(post(APPLY_GUARD))).isFalse();
        }

        @Test
        @DisplayName("the literal auth paths still match exactly")
        void literalPathsStillMatch() throws Exception {
            authProperties.getRateLimit().getLogin().setCapacity(1);

            assertThat(passesThrough(post("/api/v1/auth/login"))).isTrue();
            assertThat(passesThrough(post("/api/v1/auth/login"))).isFalse();
        }

        @Test
        @DisplayName("an unguarded path is passed straight through")
        void unguardedPathsAreUntouched() throws Exception {
            // Called many more times than any capacity, to prove no bucket is
            // being consumed behind the scenes.
            for (int i = 0; i < 50; i++) {
                assertThat(passesThrough(post("/api/v1/clients"))).isTrue();
            }
        }

        @Test
        @DisplayName("GET on a guarded path is not limited")
        void onlyPostIsLimited() throws Exception {
            // Every rule here guards a submission. Limiting the careers page
            // itself would break browsing.
            for (int i = 0; i < 50; i++) {
                MockHttpServletRequest request = new MockHttpServletRequest("GET", APPLY_GUARD);
                request.setRemoteAddr("203.0.113.4");
                assertThat(passesThrough(request)).isTrue();
            }
        }

        @Test
        @DisplayName("a path that only resembles the pattern is not guarded")
        void nearMissesAreNotGuarded() throws Exception {
            // One segment short, and one segment long: neither is the apply
            // endpoint, and matching either would limit the wrong thing.
            for (int i = 0; i < 20; i++) {
                assertThat(passesThrough(post("/api/v1/public/jobs/apply"))).isTrue();
                assertThat(passesThrough(post("/api/v1/public/jobs/a/b/apply"))).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("bucket keying")
    class BucketKeying {

        @Test
        @DisplayName("every job slug shares one allowance")
        void slugsShareABucket() throws Exception {
            // The bug this guards: keying on the concrete path would give each
            // vacancy its own budget, so an attacker with ten open jobs to aim
            // at would get ten times the allowance.
            assertThat(passesThrough(post(APPLY_GUARD))).isTrue();
            assertThat(passesThrough(post(APPLY_HELPER))).isTrue();

            // Capacity is 2 and two have been spent, on different slugs.
            assertThat(passesThrough(post("/api/v1/public/jobs/a-third-job/apply")))
                    .isFalse();
        }

        @Test
        @DisplayName("different IPs get separate allowances")
        void ipsDoNotShareABucket() throws Exception {
            assertThat(passesThrough(post(APPLY_GUARD, "203.0.113.4"))).isTrue();
            assertThat(passesThrough(post(APPLY_GUARD, "203.0.113.4"))).isTrue();
            assertThat(passesThrough(post(APPLY_GUARD, "203.0.113.4"))).isFalse();

            // A different applicant behind a different address is unaffected.
            assertThat(passesThrough(post(APPLY_GUARD, "198.51.100.7"))).isTrue();
        }

        @Test
        @DisplayName("exhausting one endpoint does not block another")
        void endpointsDoNotShareABucket() throws Exception {
            assertThat(passesThrough(post(APPLY_GUARD))).isTrue();
            assertThat(passesThrough(post(APPLY_GUARD))).isTrue();
            assertThat(passesThrough(post(APPLY_GUARD))).isFalse();

            // The enquiry form has its own bucket even though it shares the
            // same rule object.
            assertThat(passesThrough(post("/api/v1/public/enquiries"))).isTrue();
        }
    }

    @Nested
    @DisplayName("the disable switch")
    class DisableSwitch {

        @Test
        @DisplayName("nothing is limited when rate limiting is turned off")
        void disabledMeansNoLimiting() throws Exception {
            authProperties.getRateLimit().setEnabled(false);

            for (int i = 0; i < 20; i++) {
                assertThat(passesThrough(post(APPLY_GUARD))).isTrue();
            }
        }
    }

    // ------------------------------------------------------------------

    /**
     * Runs the filter and reports whether the request reached the chain.
     *
     * <p>False means the filter refused it - which is also the assertion that
     * the chain was <em>not</em> continued after the response was written, the
     * mistake that produces a committed-response error rather than a clean 429.
     */
    private boolean passesThrough(MockHttpServletRequest request) throws Exception {
        FilterChain thisCall = Mockito.mock(FilterChain.class);
        filter.doFilter(request, new MockHttpServletResponse(), thisCall);
        return Mockito.mockingDetails(thisCall).getInvocations().size() == 1;
    }

    private static MockHttpServletRequest post(String path) {
        return post(path, "203.0.113.4");
    }

    private static MockHttpServletRequest post(String path, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRequestURI(path);
        request.setRemoteAddr(ip);
        return request;
    }
}
