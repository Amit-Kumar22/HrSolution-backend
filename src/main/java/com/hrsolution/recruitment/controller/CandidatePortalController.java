package com.hrsolution.recruitment.controller;

import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.security.Permissions;
import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.recruitment.dto.ApplicationDtos;
import com.hrsolution.recruitment.dto.CandidateDtos;
import com.hrsolution.recruitment.service.ApplicationService;
import com.hrsolution.recruitment.service.CandidateService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * The signed-in candidate's own portal.
 *
 * <p>Everything here is scoped to the caller. There are no ids to widen: the
 * profile endpoints resolve the candidate from the token, and the application
 * endpoints answer {@code 404} for an application belonging to anyone else -
 * never {@code 403}, so the API does not confirm that an id exists.
 *
 * <p>What a candidate is deliberately <strong>not</strong> shown: which
 * recruiter owns their application, the notes on the funnel trail, and the
 * interview panel's feedback and rating. They are entitled to know where they
 * stand, not to read the write-up. {@link CandidateDtos.SelfResponse} and
 * {@link ApplicationDtos.SelfResponse} are separate records from the staff ones
 * so that stays structural.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/me")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Candidate portal",
        description = "The signed-in candidate's own profile and applications")
public class CandidatePortalController {

    private final CandidateService candidateService;
    private final ApplicationService applicationService;

    // ==================================================================
    // Profile
    // ==================================================================

    @GetMapping("/candidate-profile")
    @PreAuthorize("hasAuthority('" + Permissions.SELF_PROFILE_MANAGE + "')")
    @Operation(summary = "Get your own applicant profile")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Your profile"),
            @ApiResponse(responseCode = "422",
                    description = "You have no applicant profile yet - apply for a job or "
                            + "complete your profile to create one (BUSINESS_RULE_VIOLATION)")
    })
    public CandidateDtos.SelfResponse getProfile(
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return candidateService.getOwnProfile(caller);
    }

    @PutMapping("/candidate-profile")
    @PreAuthorize("hasAuthority('" + Permissions.SELF_PROFILE_MANAGE + "')")
    @Operation(summary = "Update your own applicant profile",
            description = "Your email is not editable: it identifies you across every "
                    + "application, so changing it would orphan your history.")
    public CandidateDtos.SelfResponse updateProfile(
            @Valid @RequestBody CandidateDtos.SelfRequest request,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return candidateService.updateOwnProfile(
                request, caller, RequestContext.from(httpRequest));
    }

    @PostMapping(path = "/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('" + Permissions.SELF_PROFILE_MANAGE + "')")
    @Operation(summary = "Upload or replace your CV",
            description = "PDF only, verified by content. Replaces the CV on your profile; the "
                    + "copy attached to each past application is a snapshot and stays as it "
                    + "was sent.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Your updated profile"),
            @ApiResponse(responseCode = "413", description = "Over 10 MB (PAYLOAD_TOO_LARGE)"),
            @ApiResponse(responseCode = "415",
                    description = "Not a PDF by content (UNSUPPORTED_MEDIA_TYPE)")
    })
    public CandidateDtos.SelfResponse uploadResume(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return candidateService.uploadOwnResume(file, caller, RequestContext.from(httpRequest));
    }

    // ==================================================================
    // Applications
    // ==================================================================

    @GetMapping("/applications")
    @PreAuthorize("hasAuthority('" + Permissions.SELF_APPLICATION_MANAGE + "')")
    @Operation(summary = "List your own applications",
            description = "Newest first. Includes an application you submitted as a guest "
                    + "before registering, if it used this email address.")
    public List<ApplicationDtos.SelfResponse> listApplications(
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return applicationService.listOwn(caller);
    }

    @GetMapping("/applications/{applicationId}")
    @PreAuthorize("hasAuthority('" + Permissions.SELF_APPLICATION_MANAGE + "')")
    @Operation(summary = "Get one of your own applications")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The application"),
            @ApiResponse(responseCode = "404",
                    description = "No such application, or not yours (RESOURCE_NOT_FOUND)")
    })
    public ApplicationDtos.SelfResponse getApplication(
            @PathVariable Long applicationId,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return applicationService.getOwn(applicationId, caller);
    }

    @PostMapping("/applications/jobs/{jobId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.SELF_APPLICATION_MANAGE + "')")
    @Operation(summary = "Apply for a job",
            description = "Uses the CV on your profile as it stands now, snapshotted against "
                    + "this application. Upload one first with POST /me/resume if you have none.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Application received"),
            @ApiResponse(responseCode = "409",
                    description = "You have already applied for this job (DUPLICATE_RESOURCE)"),
            @ApiResponse(responseCode = "422",
                    description = "The job is not accepting applications, or has closed "
                            + "(BUSINESS_RULE_VIOLATION)")
    })
    public ApplicationDtos.ApplyResponse apply(
            @PathVariable Long jobId,
            @Valid @RequestBody ApplicationDtos.SelfApplyRequest request,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return applicationService.applyAsCandidate(
                jobId, request, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/applications/{applicationId}/withdraw")
    @PreAuthorize("hasAuthority('" + Permissions.SELF_APPLICATION_MANAGE + "')")
    @Operation(summary = "Withdraw your own application",
            description = "Terminal. Re-applying afterwards is not possible on the same "
                    + "vacancy, so confirm before calling.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The withdrawn application"),
            @ApiResponse(responseCode = "404",
                    description = "No such application, or not yours (RESOURCE_NOT_FOUND)"),
            @ApiResponse(responseCode = "422",
                    description = "Already in a final state (BUSINESS_RULE_VIOLATION)")
    })
    public ApplicationDtos.SelfResponse withdraw(
            @PathVariable Long applicationId,
            @Valid @RequestBody ApplicationDtos.WithdrawRequest request,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return applicationService.withdrawOwn(
                applicationId, request, caller, RequestContext.from(httpRequest));
    }
}
