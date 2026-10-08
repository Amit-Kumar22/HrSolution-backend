package com.hrsolution.document.controller;

import com.hrsolution.common.web.PublicFileUrls;
import com.hrsolution.document.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Serves public marketing assets — the company logo, testimonial photos and
 * service hero images — without authentication.
 *
 * <p><strong>The security boundary is the {@code publicAsset} flag</strong>,
 * checked by {@code DocumentService.downloadPublicAsset}. Without it, knowing or
 * guessing a storage key would expose every worker's identity documents through
 * an anonymous endpoint. A key that is not flagged public returns 404 rather
 * than 403, so this route cannot be used to confirm that a private document
 * exists.
 *
 * <p>Separate from {@code DocumentController} on purpose. One route is anonymous
 * and one is not, and keeping them in different classes means the anonymous one
 * cannot quietly inherit a change intended for the other.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Public website",
        description = "Unauthenticated endpoints for the marketing site: content, company "
                + "details, and the enquiry and contact forms")
public class PublicFileController {

    private final DocumentService documentService;

    /**
     * Fetches a public asset.
     *
     * <p>The key contains slashes ({@code logos/2026/10/uuid.png}), so the
     * mapping ends in {@code **} and the key is recovered from the request URI.
     *
     * <p>There is deliberately no {@code @PathVariable} here. A multi-segment
     * wildcard has no variable name to bind - {@code @PathVariable("*")} is not
     * a thing, and asking for it fails at runtime with
     * {@code MissingPathVariableException} on every request.
     *
     * <p>Traversal is not a concern despite the raw URI: the key is looked up in
     * the database and must match a row flagged as a public asset, and
     * {@code LocalStorageService} independently refuses to resolve a path
     * outside the storage root.
     */
    @GetMapping(PublicFileUrls.PUBLIC_FILE_PATH + "**")
    @Operation(summary = "Fetch a public asset",
            description = "Logos, testimonial photos and service images, addressed by storage key "
                    + "(e.g. /api/v1/public/files/logos/2026/10/3f2a....png). Only files "
                    + "explicitly flagged as public assets are served; anything else returns 404, "
                    + "so this route cannot be used to confirm that a private document exists.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The file"),
            @ApiResponse(responseCode = "404",
                    description = "Unknown key, or the file is not a public asset (RESOURCE_NOT_FOUND)")
    })
    public ResponseEntity<InputStreamResource> serve(HttpServletRequest request) {
        String storageKey = extractStorageKey(request.getRequestURI());
        DocumentService.DownloadableDocument downloadable =
                documentService.downloadPublicAsset(storageKey);

        // Inline here, unlike the private route: these are images the site
        // embeds in <img> tags, and they are restricted to PNG, JPEG and GIF by
        // content detection at upload, none of which a browser can execute.
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(downloadable.metadata().getOriginalFileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                // Long cache: the storage key contains a UUID, so a changed
                // image always has a different URL and can never be stale.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .contentType(MediaType.parseMediaType(downloadable.metadata().getContentType()))
                .contentLength(downloadable.metadata().getSizeBytes())
                .body(new InputStreamResource(downloadable.content()));
    }

    private String extractStorageKey(String requestUri) {
        int prefixEnd = requestUri.indexOf(PublicFileUrls.PUBLIC_FILE_PATH);
        if (prefixEnd < 0) {
            return "";
        }
        return requestUri.substring(prefixEnd + PublicFileUrls.PUBLIC_FILE_PATH.length());
    }
}
