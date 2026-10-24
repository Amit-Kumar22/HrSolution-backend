package com.hrsolution.worker.controller;

import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.security.Permissions;
import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.worker.dto.WorkerDocumentDtos;
import com.hrsolution.worker.service.WorkerDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Worker documents - the evidence file.
 *
 * <p>The file itself goes through Phase 3's document storage, so download it
 * with {@code GET /documents/{documentId}/download} using the {@code documentId}
 * on each response - not the {@code id} of the worker-document row.
 */
@RestController
@RequestMapping(ApiPaths.V1)
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Worker documents", description = "Identity, verification and compliance documents")
public class WorkerDocumentController {

    private final WorkerDocumentService documentService;

    @GetMapping("/workers/{workerId}/documents")
    @PreAuthorize("hasAuthority('" + Permissions.WORKER_READ + "')")
    @Operation(summary = "List a worker's documents",
            description = "Use `documentId` with GET /documents/{id}/download to fetch a file.")
    public List<WorkerDocumentDtos.Response> list(@PathVariable Long workerId) {
        return documentService.listForWorker(workerId);
    }

    @GetMapping("/worker-documents/{workerDocumentId}")
    @PreAuthorize("hasAuthority('" + Permissions.WORKER_READ + "')")
    @Operation(summary = "Get one worker document")
    public WorkerDocumentDtos.Response get(@PathVariable Long workerDocumentId) {
        return documentService.get(workerDocumentId);
    }

    @GetMapping("/worker-documents/expiring")
    @PreAuthorize("hasAuthority('" + Permissions.COMPLIANCE_READ + "')")
    @Operation(summary = "Documents lapsing soon",
            description = """
                    Verified documents with an expiry date on or before the cutoff. A lapsed
                    police verification or medical certificate is a compliance finding, not an
                    inconvenience, so this belongs on a dashboard.

                    Only verified ones: chasing the renewal of a document nobody checked in the
                    first place is the wrong conversation.""")
    public List<WorkerDocumentDtos.Response> listExpiring(
            @Parameter(description = "How far ahead to look")
            @RequestParam(defaultValue = "30") int withinDays) {
        return documentService.listExpiring(withinDays);
    }

    @PostMapping(path = "/workers/{workerId}/documents",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.WORKER_WRITE + "')")
    @Operation(summary = "Upload a worker document",
            description = """
                    Multipart with two parts: `metadata` (JSON, a `WorkerDocumentUploadRequest`)
                    and `file`.

                    PDF, PNG and JPEG only, verified by file content rather than extension.

                    `POLICE_VERIFICATION`, `MEDICAL` and `LICENCE` normally renew, so supply
                    `expiresOn` for those - it is what drives the reminders. Omitting it is
                    allowed but the document will never be chased.

                    `referenceNumber` is the number on the certificate. It is **not** the
                    Aadhaar or PAN: those are encrypted on the worker record, and copying them
                    here in clear would defeat that.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Uploaded, status PENDING"),
            @ApiResponse(responseCode = "413", description = "Over 10 MB (PAYLOAD_TOO_LARGE)"),
            @ApiResponse(responseCode = "415",
                    description = "Not a PDF, PNG or JPEG by content (UNSUPPORTED_MEDIA_TYPE)"),
            @ApiResponse(responseCode = "422",
                    description = "The expiry date is before the issue date "
                            + "(BUSINESS_RULE_VIOLATION)")
    })
    public WorkerDocumentDtos.Response upload(
            @PathVariable Long workerId,
            @Valid @RequestPart("metadata") WorkerDocumentDtos.UploadRequest metadata,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return documentService.upload(
                workerId, metadata, file, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/worker-documents/{workerDocumentId}/verification")
    @PreAuthorize("hasAuthority('" + Permissions.WORKER_WRITE + "')")
    @Operation(summary = "Accept or reject a document",
            description = """
                    `VERIFIED` or `REJECTED`. Write-once: a decided document cannot be
                    re-decided, because the decision is itself the evidence. Upload a
                    replacement if it needs looking at again - both stay on file.

                    A reason is required when rejecting; whoever has to obtain a replacement
                    needs to know what was wrong with this one.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated document"),
            @ApiResponse(responseCode = "422",
                    description = "Already decided, PENDING sent as a decision, or a rejection "
                            + "with no reason (BUSINESS_RULE_VIOLATION)")
    })
    public WorkerDocumentDtos.Response verify(
            @PathVariable Long workerDocumentId,
            @Valid @RequestBody WorkerDocumentDtos.VerificationRequest request,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return documentService.verify(
                workerDocumentId, request, caller, RequestContext.from(httpRequest));
    }

    @DeleteMapping("/worker-documents/{workerDocumentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('" + Permissions.WORKER_WRITE + "')")
    @Operation(summary = "Remove a document",
            description = "Soft delete. A verified document is compliance evidence and cannot "
                    + "be removed - upload a replacement instead.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Removed"),
            @ApiResponse(responseCode = "422",
                    description = "The document is verified (BUSINESS_RULE_VIOLATION)")
    })
    public void delete(@PathVariable Long workerDocumentId,
                       @AuthenticationPrincipal AuthenticatedUser caller,
                       HttpServletRequest httpRequest) {
        documentService.delete(workerDocumentId, caller, RequestContext.from(httpRequest));
    }
}
