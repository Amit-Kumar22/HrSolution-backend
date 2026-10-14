package com.hrsolution.recruitment.controller;

import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.security.Permissions;
import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.recruitment.dto.CandidateDtos;
import com.hrsolution.recruitment.entity.CandidateSource;
import com.hrsolution.recruitment.service.CandidateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Candidates, for staff.
 *
 * <p>A candidate's email is their identity across every application, so there
 * is no endpoint that changes it - {@code PUT} refuses a different address
 * rather than silently re-pointing an application history onto another person.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/candidates")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Candidates", description = "People in the hiring pipeline")
public class CandidateController {

    private final CandidateService candidateService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.CANDIDATE_READ + "')")
    @Operation(summary = "List candidates",
            description = "Spam-flagged candidates are hidden unless includeSpam is set. They "
                    + "are stored rather than rejected, so give staff a toggle - a honeypot "
                    + "false positive is a real applicant.")
    public PageResponse<CandidateDtos.SummaryResponse> list(
            @Parameter(description = "Matches name, email, phone or city")
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long preferredCategoryId,
            @RequestParam(required = false) CandidateSource source,
            @Parameter(description = "true for registered candidates only, false for guests only")
            @RequestParam(required = false) Boolean hasLogin,
            @Parameter(description = "Include candidates flagged as possible spam")
            @RequestParam(defaultValue = "false") boolean includeSpam,
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return candidateService.list(
                search, preferredCategoryId, source, hasLogin, includeSpam, pageable);
    }

    @GetMapping("/{candidateId}")
    @PreAuthorize("hasAuthority('" + Permissions.CANDIDATE_READ + "')")
    @Operation(summary = "Get one candidate, including internal notes")
    public CandidateDtos.Response get(@PathVariable Long candidateId) {
        return candidateService.get(candidateId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.CANDIDATE_WRITE + "')")
    @Operation(summary = "Create a candidate",
            description = "For walk-ins and agency CVs. Defaults source to WALK_IN rather than "
                    + "WEBSITE, since attributing a walk-in to the website overstates the "
                    + "channel that costs money. consentGiven must be true.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "409",
                    description = "A candidate with that email exists - open it and add the "
                            + "application there (DUPLICATE_RESOURCE)"),
            @ApiResponse(responseCode = "422",
                    description = "Consent was not confirmed (BUSINESS_RULE_VIOLATION)")
    })
    public CandidateDtos.Response create(@Valid @RequestBody CandidateDtos.Request request,
                                         @AuthenticationPrincipal AuthenticatedUser caller,
                                         HttpServletRequest httpRequest) {
        return candidateService.create(request, caller, RequestContext.from(httpRequest));
    }

    @PutMapping("/{candidateId}")
    @PreAuthorize("hasAuthority('" + Permissions.CANDIDATE_WRITE + "')")
    @Operation(summary = "Update a candidate",
            description = "The email in the body must match the stored one. It identifies the "
                    + "person across every application, so changing it is refused rather than "
                    + "silently re-pointing their history.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated candidate"),
            @ApiResponse(responseCode = "422",
                    description = "A different email was sent (BUSINESS_RULE_VIOLATION)")
    })
    public CandidateDtos.Response update(@PathVariable Long candidateId,
                                         @Valid @RequestBody CandidateDtos.Request request,
                                         @AuthenticationPrincipal AuthenticatedUser caller,
                                         HttpServletRequest httpRequest) {
        return candidateService.update(
                candidateId, request, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/{candidateId}/spam")
    @PreAuthorize("hasAuthority('" + Permissions.CANDIDATE_WRITE + "')")
    @Operation(summary = "Flag or clear a candidate as spam",
            description = "Clearing the flag is how a honeypot false positive is rescued.")
    public CandidateDtos.Response setSpam(
            @PathVariable Long candidateId,
            @RequestParam(defaultValue = "true") boolean spam,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return candidateService.setSpam(
                candidateId, spam, caller, RequestContext.from(httpRequest));
    }

    @PostMapping(path = "/{candidateId}/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('" + Permissions.CANDIDATE_WRITE + "')")
    @Operation(summary = "Upload or replace a candidate's CV",
            description = "PDF only, verified by file content rather than extension - a Word "
                    + "document renamed to .pdf is rejected here rather than opened by "
                    + "whoever downloads it. Replaces the candidate's current CV; CVs already "
                    + "attached to past applications are untouched.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated candidate"),
            @ApiResponse(responseCode = "413", description = "Over 10 MB (PAYLOAD_TOO_LARGE)"),
            @ApiResponse(responseCode = "415",
                    description = "Not a PDF by content (UNSUPPORTED_MEDIA_TYPE)")
    })
    public CandidateDtos.Response uploadResume(@PathVariable Long candidateId,
                                               @RequestPart("file") MultipartFile file,
                                               @AuthenticationPrincipal AuthenticatedUser caller,
                                               HttpServletRequest httpRequest) {
        return candidateService.uploadResume(
                candidateId, file, caller, RequestContext.from(httpRequest));
    }

    @DeleteMapping("/{candidateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('" + Permissions.CANDIDATE_WRITE + "')")
    @Operation(summary = "Delete a candidate",
            description = "Soft delete, and how a consent withdrawal is honoured. Refused once "
                    + "the person has joined: the recruitment record is then evidence that the "
                    + "hire was vetted, which outlives consent to hold marketing data.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted"),
            @ApiResponse(responseCode = "422",
                    description = "The candidate has joined (BUSINESS_RULE_VIOLATION)")
    })
    public void delete(@PathVariable Long candidateId,
                       @AuthenticationPrincipal AuthenticatedUser caller,
                       HttpServletRequest httpRequest) {
        candidateService.delete(candidateId, caller, RequestContext.from(httpRequest));
    }
}
