package com.hrsolution.seo.controller;

import com.hrsolution.common.config.AppProperties;
import com.hrsolution.content.dto.ContentDtos;
import com.hrsolution.content.service.ContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code robots.txt} and {@code sitemap.xml}.
 *
 * <p>Served by the backend rather than written as static files because the
 * sitemap has to list the service pages that actually exist and are published —
 * a hand-maintained file drifts the first time someone adds a page, and a
 * sitemap listing a 404 is worse than no sitemap.
 *
 * <p>At the site root, not under {@code /api/v1}: both files are only honoured
 * by crawlers at a domain's root. When the marketing site is served from its own
 * domain, these two routes need proxying to this backend.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "SEO", description = "robots.txt and sitemap.xml for search engines")
public class SeoController {

    private final AppProperties appProperties;
    private final ContentService contentService;

    /** Static pages the marketing site is expected to have, with crawl priorities. */
    private static final List<StaticPage> STATIC_PAGES = List.of(
            new StaticPage("", "1.0", "weekly"),
            new StaticPage("about-us", "0.8", "monthly"),
            new StaticPage("services", "0.9", "monthly"),
            new StaticPage("industries", "0.7", "monthly"),
            new StaticPage("current-openings", "0.9", "daily"),
            new StaticPage("clients", "0.6", "monthly"),
            new StaticPage("contact-us", "0.8", "monthly"),
            new StaticPage("privacy-policy", "0.3", "yearly"),
            new StaticPage("terms", "0.3", "yearly"));

    private record StaticPage(String path, String priority, String changeFrequency) {
    }

    /**
     * {@code robots.txt}.
     *
     * <p>Defaults to {@code Disallow: /} — indexing is off unless
     * {@code app.site.seo-indexing-enabled} is explicitly true. That default is
     * deliberate: a staging or dev host that gets indexed competes with
     * production for the same search terms and is very awkward to undo, so
     * indexing should be something a deployment opts into.
     */
    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "robots.txt",
            description = "Disallows everything unless app.site.seo-indexing-enabled is true, so "
                    + "a staging host cannot be indexed by accident.")
    public ResponseEntity<String> robotsTxt() {
        AppProperties.Site site = appProperties.getSite();

        if (!site.isSeoIndexingEnabled()) {
            return ResponseEntity.ok("""
                    # Indexing is disabled for this environment.
                    # Set app.site.seo-indexing-enabled=true in production.
                    User-agent: *
                    Disallow: /
                    """);
        }

        return ResponseEntity.ok("""
                User-agent: *
                Allow: /

                # The portal and the API are of no use to a crawler, and keeping
                # them out of the index avoids publishing the API surface.
                Disallow: /api/
                Disallow: /portal/
                Disallow: /actuator/
                Disallow: /swagger-ui/
                Disallow: /v3/api-docs

                Sitemap: %s/sitemap.xml
                """.formatted(trimTrailingSlash(site.getBaseUrl())));
    }

    /**
     * {@code sitemap.xml}, listing the static pages plus every published service.
     *
     * <p>Returns an empty but valid sitemap when indexing is disabled, rather
     * than a 404 — a crawler that already knows the URL gets a clear "nothing
     * here" instead of an error.
     */
    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "sitemap.xml",
            description = "Generated from the published content, so it can never list a page that "
                    + "does not exist. Empty when indexing is disabled.")
    public ResponseEntity<String> sitemapXml() {
        AppProperties.Site site = appProperties.getSite();
        String baseUrl = trimTrailingSlash(site.getBaseUrl());

        StringBuilder xml = new StringBuilder(2048);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        if (site.isSeoIndexingEnabled()) {
            String today = LocalDate.now().toString();

            for (StaticPage page : STATIC_PAGES) {
                appendUrl(xml, baseUrl, page.path(), today, page.changeFrequency(), page.priority());
            }

            // Driven by the database, which is the point of generating this.
            for (ContentDtos.PublicServiceResponse service : contentService.publishedServices()) {
                appendUrl(xml, baseUrl, "services/" + service.slug(), today, "monthly", "0.8");
            }

            for (ContentDtos.PublicIndustryResponse industry : contentService.publishedIndustries()) {
                appendUrl(xml, baseUrl, "industries/" + industry.slug(), today, "monthly", "0.6");
            }
        } else {
            xml.append("  <!-- Indexing is disabled for this environment. -->\n");
        }

        xml.append("</urlset>\n");
        return ResponseEntity.ok(xml.toString());
    }

    private void appendUrl(StringBuilder xml, String baseUrl, String path,
                           String lastModified, String changeFrequency, String priority) {
        String location = path.isEmpty() ? baseUrl + "/" : baseUrl + "/" + path;
        xml.append("  <url>\n")
                .append("    <loc>").append(escapeXml(location)).append("</loc>\n")
                .append("    <lastmod>").append(lastModified).append("</lastmod>\n")
                .append("    <changefreq>").append(changeFrequency).append("</changefreq>\n")
                .append("    <priority>").append(priority).append("</priority>\n")
                .append("  </url>\n");
    }

    /**
     * Escapes the five XML entities.
     *
     * <p>Slugs are already restricted to lower-case letters, digits and hyphens,
     * so nothing here can currently need escaping — but the base URL comes from
     * configuration, and an unescaped ampersand in it would produce a sitemap
     * that fails to parse and is silently ignored by every crawler.
     */
    private String escapeXml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String trimTrailingSlash(String url) {
        return url == null ? "" : url.replaceAll("/+$", "");
    }
}
