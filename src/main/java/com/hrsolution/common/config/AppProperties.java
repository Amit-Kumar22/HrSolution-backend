package com.hrsolution.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Typed binding for the application's own {@code app.*} settings.
 *
 * <p>Picked up by {@code @ConfigurationPropertiesScan} on the main class, so no
 * {@code @Component} or {@code @EnableConfigurationProperties} is needed.
 * Inject it like any other bean.
 *
 * <p>Add a new setting by declaring a field here plus a default in
 * {@code application.properties}; the IDE will then autocomplete it, because
 * {@code spring-boot-configuration-processor} generates metadata at compile time.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Cors cors = new Cors();
    private final OpenApi openapi = new OpenApi();
    private final Mail mail = new Mail();
    private final Storage storage = new Storage();
    private final Site site = new Site();

    @Getter
    @Setter
    public static class Storage {

        /** Where {@code LocalStorageService} writes. Gitignored. */
        private String localPath = "./uploads";

        /**
         * Per-file cap, enforced before anything is written.
         *
         * <p>Separate from {@code spring.servlet.multipart.max-file-size}, which
         * Tomcat applies to the whole request. This one is per logical upload
         * and can be tightened for a specific kind of file.
         */
        private long maxFileSizeBytes = 10L * 1024 * 1024;

        /** Tighter cap for images, which never legitimately need 10 MB. */
        private long maxImageSizeBytes = 2L * 1024 * 1024;
    }

    @Getter
    @Setter
    public static class Site {

        /**
         * Public base URL of the marketing site. Used to build absolute URLs in
         * {@code sitemap.xml}, which search engines require.
         */
        private String baseUrl = "http://localhost:8080";

        /**
         * Whether to let search engines index the site. False emits a
         * {@code Disallow: /} robots.txt - the correct setting for a staging
         * host, where an indexed copy competes with production for rankings.
         */
        private boolean seoIndexingEnabled = false;
    }

    @Getter
    @Setter
    public static class Mail {

        /** Envelope sender for all outbound mail. */
        private String from = "no-reply@hrsolution.local";

        /** Where registration and enquiry notifications go. */
        private String adminRecipient = "admin@hrsolution.local";

        /**
         * Base URL that email links point at - the address of the site a user
         * lands on, which is not necessarily this API. Verification and reset
         * links are built as {@code {baseUrl}/verify-email?token=...}.
         */
        private String baseUrl = "http://localhost:8080";

        /**
         * Turn off to stop sending entirely. Messages are logged instead, which
         * is what tests use so that no SMTP server is needed and a mail outage
         * cannot fail an unrelated assertion.
         */
        private boolean enabled = true;

        /** Attempts per message before giving up. */
        private int maxAttempts = 3;

        /** Delay before the first retry; doubles on each subsequent attempt. */
        private long retryDelayMillis = 2000;
    }

    @Getter
    @Setter
    public static class Cors {

        /**
         * Browser origins permitted to call this API. Empty by default: a
         * misconfigured deployment should fail closed rather than accept
         * requests from anywhere.
         */
        private List<String> allowedOrigins = List.of();

        private List<String> allowedMethods =
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

        private List<String> allowedHeaders = List.of("*");

        /** Response headers a browser client is allowed to read. */
        private List<String> exposedHeaders = List.of("X-Correlation-Id");

        /** Required for the refresh-token cookie introduced in Phase 2. */
        private boolean allowCredentials = true;

        /** Seconds a browser may cache the CORS preflight result. */
        private long maxAgeSeconds = 3600;
    }

    @Getter
    @Setter
    public static class OpenApi {
        private String title = "HR Solution API";
        private String version = "v1";
        private String description = "HR Solutions and Manpower Supply Platform API";
        private String contactName = "";
        private String contactEmail = "";
    }
}
