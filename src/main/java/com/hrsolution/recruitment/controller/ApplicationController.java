package com.hrsolution.recruitment.controller;

import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.security.Permissions;
import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.recruitment.dto.ApplicationDtos;
import com.hrsolution.recruitment.entity.ApplicationStatus;
import com.hrsolution.recruitment.service.ApplicationService;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Applications, for staff - the pipeline board.
 *
 * <p>There is deliberately <strong>no delete</strong>. An application that
 * should go away is {@code WITHDRAWN} or {@code REJECTED}; deleting one would
 * break the unique constraint that stops a candidate applying twice, and would
 * remove the funnel history a payroll or compliance dispute is argued from.
 *
 * <p>Drive the status dropdown from {@code allowedNextStatuses} on the
 * response, not from the full enum. The transitions are a constrained graph and
 * offering an illegal one just produces a 422 the user cannot act on.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/applications")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Applications", description = "The hiring funnel")
public class ApplicationController {

    private final ApplicationService applicationService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.APPLICATION_READ + "')")
    @Operation(summary = "List applications",
            description = "The pipeline board. Filter by job for one vacancy's funnel, or by "
                    + "unassigned=true for the queue nobody owns yet.")
    public PageResponse<ApplicationDtos.SummaryResponse> list(
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) Long candidateId,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) Long assignedToUserId,
            @Parameter(description = "true for applications with no owning recruiter")
            @RequestParam(required = false) Boolean unassigned,
            @Parameter(description = "Applied on or after this date")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Applied on or before this date")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Matches the candidate's name, email or phone")
            @RequestParam(required = false) String search,
            @ParameterObject
            @PageableDefault(size = 20, sort = "appliedOn", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return applicationService.list(jobId, candidateId, status, assignedToUserId, unassigned,
                from, to, search, pageable);
    }

    @GetMapping("/{applicationId}")
    @PreAuthorize("hasAuthority('" + Permissions.APPLICATION_READ + "')")
    @Operation(summary = "Get one application")
    public ApplicationDtos.Response get(@PathVariable Long applicationId) {
        return applicationService.get(applicationId);
    }

    @GetMapping("/{applicationId}/detail")
    @PreAuthorize("hasAuthority('" + Permissions.APPLICATION_READ + "')")
    @Operation(summary = "Get an application with its funnel trail and interviews",
            description = "One call for the detail drawer. The history is oldest first and its "
                    + "first entry has a null fromStatus, recording the submission.")
    public ApplicationDtos.DetailResponse getDetail(@PathVariable Long applicationId) {
        return applicationService.getDetail(applicationId);
    }

    @PatchMapping("/{applicationId}/status")
    @PreAuthorize("hasAuthority('" + Permissions.APPLICATION_MANAGE + "')")
    @Operation(summary = "Move an application along the funnel",
            description = """
                    Only the moves in the response's `allowedNextStatuses` are accepted.

                    Two rules worth knowing:

                    * a `note` is **required** when rejecting - it is all the next recruiter
                      to open the record will have
                    * `INTERVIEW_SCHEDULED` cannot be set by hand; scheduling an interview
                      sets it, so the status cannot claim a meeting that does not exist""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated application"),
            @ApiResponse(responseCode = "422",
                    description = "Illegal transition, a rejection with no reason, or "
                            + "INTERVIEW_SCHEDULED with no interview (BUSINESS_RULE_VIOLATION)")
    })
    public ApplicationDtos.Response changeStatus(
            @PathVariable Long applicationId,
            @Valid @RequestBody ApplicationDtos.StatusChangeRequest request,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return applicationService.changeStatus(
                applicationId, request, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/{applicationId}/assign")
    @PreAuthorize("hasAuthority('" + Permissions.APPLICATION_MANAGE + "')")
    @Operation(summary = "Assign an application to a recruiter",
            description = "Send a null assignedToUserId to unassign.")
    public ApplicationDtos.Response assign(
            @PathVariable Long applicationId,
            @Valid @RequestBody ApplicationDtos.AssignRequest request,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return applicationService.assign(
                applicationId, request, caller, RequestContext.from(httpRequest));
    }
}
