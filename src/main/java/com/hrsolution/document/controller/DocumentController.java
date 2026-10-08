package com.hrsolution.document.controller;

import com.hrsolution.document.dto.DocumentResponse;
import com.hrsolution.document.mapper.DocumentMapper;
import com.hrsolution.document.service.DocumentService;
import com.hrsolution.common.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Authenticated download of stored documents.
 *
 * <p>Files are never served from a directory the web server exposes. Every
 * private download goes through this endpoint so that authentication is
 * enforced and the request can be attributed — which is the whole reason
 * {@code LocalStorageService} writes outside any static resource path.
 *
 * <p>Per-document authorisation lands with the owning module: Phase 6 will
 * check that a client may only fetch its own workers' documents. For now the
 * requirement is simply a valid token, which is already more than the public
 * asset route demands.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/documents")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Documents", description = "Upload metadata and authenticated downloads")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentMapper documentMapper;

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get a document's metadata")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The document metadata"),
            @ApiResponse(responseCode = "404", description = "No such document (RESOURCE_NOT_FOUND)")
    })
    public DocumentResponse get(@PathVariable Long id) {
        return documentMapper.toResponse(documentService.get(id));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Download a document",
            description = "Served as an attachment with the content type detected at upload "
                    + "time, never the type the uploader declared.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The file"),
            @ApiResponse(responseCode = "401", description = "Not signed in (UNAUTHENTICATED)"),
            @ApiResponse(responseCode = "404", description = "No such document (RESOURCE_NOT_FOUND)")
    })
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) {
        DocumentService.DownloadableDocument downloadable = documentService.download(id);

        // Attachment, not inline. An inline Content-Disposition asks the browser
        // to render the file on this application's origin, which for anything
        // that could be interpreted as HTML would be stored XSS. Forcing a
        // download removes that possibility for private files entirely.
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(downloadable.metadata().getOriginalFileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                // Belt and braces with the content type: tells the browser not
                // to second-guess it and sniff the bytes instead.
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=0, no-store")
                .contentType(MediaType.parseMediaType(downloadable.metadata().getContentType()))
                .contentLength(downloadable.metadata().getSizeBytes())
                .body(new InputStreamResource(downloadable.content()));
    }
}
