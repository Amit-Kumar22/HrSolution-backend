package com.hrsolution.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.time.Duration;

/**
 * Everything tunable about authentication, under {@code app.auth.*}.
 *
 * <p>Token lifetimes, lockout thresholds and rate limits are configuration
 * rather than constants so they can be tightened per environment without a
 * rebuild - and so tests can shrink them instead of sleeping.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private final Jwt jwt = new Jwt();
    private final Refresh refresh = new Refresh();
    private final Lockout lockout = new Lockout();
    private final EmailVerification emailVerification = new EmailVerification();
    private final PasswordReset passwordReset = new PasswordReset();
    private final RateLimit rateLimit = new RateLimit();
    private final SuperAdmin superAdmin = new SuperAdmin();

    @Getter
    @Setter
    public static class Jwt {

        /** The {@code iss} claim, and what the decoder requires on inbound tokens. */
        private String issuer = "https://api.hrsolution.local";

        /**
         * Access-token lifetime. Short by design: an access token cannot be
         * revoked individually, so its window of usefulness to an attacker is
         * exactly this value. The refresh token provides continuity.
         */
        private Duration accessTokenTtl = Duration.ofMinutes(15);

        /**
         * PEM-encoded PKCS#8 RSA private key, e.g.
         * {@code file:./keys/jwt-private.pem}.
         *
         * <p>Leave both key locations unset in local development and an
         * ephemeral key pair is generated at startup - no openssl needed, at
         * the cost of every token being invalidated by a restart. The
         * application refuses to start without configured keys in any other
         * profile.
         */
        private Resource privateKeyLocation;

        /** PEM-encoded X.509 RSA public key. */
        private Resource publicKeyLocation;

        /**
         * Tolerance for clock skew between issuer and verifier. Small, but
         * non-zero: without it a token minted a fraction of a second ago can be
         * rejected as not-yet-valid on a differently-synced host.
         */
        private Duration clockSkew = Duration.ofSeconds(30);
    }

    @Getter
    @Setter
    public static class Refresh {

        private Duration ttl = Duration.ofDays(7);

        /** Lifetime when the user ticks "remember me". */
        private Duration rememberMeTtl = Duration.ofDays(30);

        private String cookieName = "hrs_refresh";

        /**
         * Cookie path. Narrow on purpose: the browser only sends the refresh
         * token to the handful of endpoints that need it, so it is not attached
         * to every ordinary API call.
         */
        private String cookiePath = "/api/v1/auth";

        /**
         * Must be true wherever HTTPS is available. False only for plain-HTTP
         * localhost, where a Secure cookie would simply never be sent.
         */
        private boolean cookieSecure = false;

        /**
         * {@code Strict} blocks the cookie on every cross-site request, which
         * is the primary CSRF defence for the refresh and logout endpoints.
         */
        private String cookieSameSite = "Strict";

        /**
         * How long revoked and expired rows are kept before the scheduled purge
         * removes them. Kept well past expiry so that a suspected token theft
         * can still be investigated.
         */
        private Duration retention = Duration.ofDays(30);
    }

    @Getter
    @Setter
    public static class Lockout {
        private int maxAttempts = 5;
        private Duration duration = Duration.ofMinutes(15);
    }

    @Getter
    @Setter
    public static class EmailVerification {
        private Duration ttl = Duration.ofHours(24);
        /** Resend cap per hour, so the endpoint cannot be used to spam an inbox. */
        private int maxPerHour = 5;
    }

    @Getter
    @Setter
    public static class PasswordReset {
        private Duration ttl = Duration.ofMinutes(30);
        private int maxPerHour = 3;
    }

    @Getter
    @Setter
    public static class RateLimit {

        private boolean enabled = true;

        /**
         * Per IP, on {@code POST /auth/login}. Looser than the per-email limit
         * because a shared office NAT puts many legitimate users behind one
         * address; its job is to stop an attacker spraying one guess each across
         * thousands of accounts.
         */
        private final Rule login = new Rule(20, Duration.ofMinutes(1));

        /**
         * Per email, on {@code POST /auth/login}.
         *
         * <p><strong>Must stay above {@link Lockout#maxAttempts}.</strong> The
         * account lockout is the real brute-force defence; if this limit bit
         * first, the fifth wrong password would return 429 and the account would
         * never actually lock - so an attacker could keep guessing a few per
         * minute forever without ever tripping the lockout.
         */
        private final Rule loginPerEmail = new Rule(10, Duration.ofMinutes(1));

        /** Per IP, on both registration endpoints. */
        private final Rule register = new Rule(5, Duration.ofHours(1));

        /** Per IP, on {@code POST /auth/forgot-password}. */
        private final Rule forgotPassword = new Rule(5, Duration.ofHours(1));

        /** Per IP, on {@code POST /auth/refresh}. Generous: legitimate clients
         *  refresh roughly every 15 minutes, but several tabs may do so at once. */
        private final Rule refresh = new Rule(30, Duration.ofMinutes(1));

        @Getter
        @Setter
        public static class Rule {

            /** Requests allowed per {@link #period}. */
            private long capacity;

            private Duration period;

            public Rule() {
            }

            public Rule(long capacity, Duration period) {
                this.capacity = capacity;
                this.period = period;
            }
        }
    }

    @Getter
    @Setter
    public static class SuperAdmin {

        /**
         * Seeded at startup if absent. The password is never written to a
         * migration file, because migrations are committed and a BCrypt hash
         * cannot be computed in SQL anyway.
         */
        private String email = "";

        private String password = "";
    }
}
