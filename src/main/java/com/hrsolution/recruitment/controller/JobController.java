package com.hrsolution.recruitment.controller;

import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.security.Permissions;
import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.recruitment.dto.ApplicationDtos;
import com.hrsolution.recruitment.dto.JobDtos;
import com.hrsolution.recruitment.entity.JobStatus;
import com.hrsolution.recruitment.service.ApplicationService;
import com.hrsolution.recruitment.service.JobService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Job adverts, for staff.
 *
 * <p>{@code JOB_READ} to look, {@code JOB_MANAGE} to change - held by
 * SUPER_ADMIN, ADMIN and HR_RECRUITER. Note that OPERATIONS_MANAGER has neither:
 * they raise the demand for workers, recruitment fills it.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/jobs")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Jobs", description = "Vacancies advertised on the careers page")
public class JobController {

    private final JobService jobService;
    private final ApplicationService applicationService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.JOB_READ + "')")
    @Operation(summary = "List jobs, including drafts and closed ones",
            description = "Unlike GET /public/jobs this returns every advert in every status.")
    public PageResponse<JobDtos.SummaryResponse> list(
            @Parameter(description = "Matches title, slug or city")
            @RequestParam(required = false) String search,
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Vacancies raised for one client")
            @RequestParam(required = false) Long clientId,
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return jobService.list(search, status, categoryId, clientId, pageable);
    }

    @GetMapping("/{jobId}")
    @PreAuthorize("hasAuthority('" + Permissions.JOB_READ + "')")
    @Operation(summary = "Get one job",
            description = "Includes the committed and remaining opening counts, which are "
                    + "derived from the applications table rather than stored.")
    public JobDtos.Response get(@PathVariable Long jobId) {
        return jobService.get(jobId);
    }

    @GetMapping("/{jobId}/pipeline")
    @PreAuthorize("hasAuthority('" + Permissions.APPLICATION_READ + "')")
    @Operation(summary = "Application counts per status for one job",
            description = "Every status is present, including the empty ones, so a board can "
                    + "render a complete set of columns.")
    public ApplicationDtos.PipelineSummary pipeline(@PathVariable Long jobId) {
        return applicationService.pipelineFor(jobId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.JOB_MANAGE + "')")
    @Operation(summary = "Create a job advert",
            description = "Always created as a DRAFT. Publishing is a separate, audited call - "
                    + "an advert created straight into OPEN would go live before anyone read "
                    + "it back.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created, as a DRAFT"),
            @ApiResponse(responseCode = "409", description = "That slug is taken (DUPLICATE_RESOURCE)"),
            @ApiResponse(responseCode = "422",
                    description = "Salary or experience range inverted, closing date in the "
                            + "past, or the site does not belong to the client "
                            + "(BUSINESS_RULE_VIOLATION)")
    })
    public JobDtos.Response create(@Valid @RequestBody JobDtos.Request request,
                                   @AuthenticationPrincipal AuthenticatedUser caller,
                                   HttpServletRequest httpRequest) {
        return jobService.create(request, caller, RequestContext.from(httpRequest));
    }

    @PutMapping("/{jobId}")
    @PreAuthorize("hasAuthority('" + Permissions.JOB_MANAGE + "')")
    @Operation(summary = "Update a job advert",
            description = "Changing the slug of a live advert breaks inbound links. Allowed, "
                    + "because a typo is worth fixing, but warn the user first.")
    public JobDtos.Response update(@PathVariable Long jobId,
                                   @Valid @RequestBody JobDtos.Request request,
                                   @AuthenticationPrincipal AuthenticatedUser caller,
                                   HttpServletRequest httpRequest) {
        return jobService.update(jobId, request, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/{jobId}/status")
    @PreAuthorize("hasAuthority('" + Permissions.JOB_MANAGE + "')")
    @Operation(summary = "Move a job through its lifecycle",
            description = "DRAFT, OPEN, ON_HOLD, CLOSED or FILLED. Only OPEN is publicly "
                    + "visible. Publishing an advert whose closing date has passed is refused.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated job"),
            @ApiResponse(responseCode = "422",
                    description = "Publishing a lapsed advert (BUSINESS_RULE_VIOLATION)")
    })
    public JobDtos.Response changeStatus(@PathVariable Long jobId,
                                         @Valid @RequestBody JobDtos.StatusChangeRequest request,
                                         @AuthenticationPrincipal AuthenticatedUser caller,
                                         HttpServletRequest httpRequest) {
        return jobService.changeStatus(jobId, request, caller, RequestContext.from(httpRequest));
    }

    @PostMapping("/{jobId}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.APPLICATION_MANAGE + "')")
    @Operation(summary = "Record an application on a candidate's behalf",
            description = "For walk-ins, phone enquiries and CVs forwarded by an agency. The "
                    + "candidate must already exist - create them with POST /candidates first.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Recorded"),
            @ApiResponse(responseCode = "409",
                    description = "This candidate already applied for this job (DUPLICATE_RESOURCE)"),
            @ApiResponse(responseCode = "422",
                    description = "The job is CLOSED or FILLED (BUSINESS_RULE_VIOLATION)")
    })
    public ApplicationDtos.Response createApplication(
            @PathVariable Long jobId,
            @Valid @RequestBody ApplicationDtos.StaffRequest request,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return applicationService.createForCandidate(
                jobId, request, caller, RequestContext.from(httpRequest));
    }

    @DeleteMapping("/{jobId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('" + Permissions.JOB_MANAGE + "')")
    @Operation(summary = "Delete a job advert",
            description = "Soft delete, and refused once applications exist - removing it would "
                    + "orphan their history. Set the status to CLOSED instead.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted"),
            @ApiResponse(responseCode = "422",
                    description = "The job has applications (BUSINESS_RULE_VIOLATION)")
    })
    public void delete(@PathVariable Long jobId,
                       @AuthenticationPrincipal AuthenticatedUser caller,
                       HttpServletRequest httpRequest) {
        jobService.delete(jobId, caller, RequestContext.from(httpRequest));
    }
}
