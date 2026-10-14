package com.hrsolution.recruitment.controller;

import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.security.Permissions;
import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.recruitment.dto.InterviewDtos;
import com.hrsolution.recruitment.entity.InterviewResult;
import com.hrsolution.recruitment.service.InterviewService;
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

import java.time.Instant;
import java.util.List;

/**
 * Interviews.
 *
 * <p>Scheduling moves the application to {@code INTERVIEW_SCHEDULED} for you,
 * and cancelling the last outstanding slot walks it back to
 * {@code SHORTLISTED}. Do not try to drive the status separately - the manual
 * route refuses {@code INTERVIEW_SCHEDULED} precisely so it cannot claim a
 * meeting that is not booked.
 */
@RestController
@RequestMapping(ApiPaths.V1)
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Interviews", description = "Scheduling, outcomes and the feedback queue")
public class InterviewController {

    private final InterviewService interviewService;

    @GetMapping("/interviews")
    @PreAuthorize("hasAuthority('" + Permissions.APPLICATION_READ + "')")
    @Operation(summary = "List interviews",
            description = "Filter by interviewerUserId for one person's diary, or "
                    + "awaitingFeedback=true for slots that have passed with no outcome - the "
                    + "list that matters most, since an interview nobody wrote up is a "
                    + "candidate left waiting.")
    public PageResponse<InterviewDtos.Response> list(
            @RequestParam(required = false) Long interviewerUserId,
            @RequestParam(required = false) InterviewResult result,
            @Parameter(description = "Scheduled at or after this instant")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Scheduled strictly before this instant")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @Parameter(description = "Only past slots with no result recorded")
            @RequestParam(required = false) Boolean awaitingFeedback,
            @ParameterObject
            @PageableDefault(size = 20, sort = "scheduledAt", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return interviewService.list(
                interviewerUserId, result, from, to, awaitingFeedback, pageable);
    }

    @GetMapping("/applications/{applicationId}/interviews")
    @PreAuthorize("hasAuthority('" + Permissions.APPLICATION_READ + "')")
    @Operation(summary = "List the interviews on one application, by round")
    public List<InterviewDtos.Response> listForApplication(@PathVariable Long applicationId) {
        return interviewService.listForApplication(applicationId);
    }

    @GetMapping("/interviews/{interviewId}")
    @PreAuthorize("hasAuthority('" + Permissions.APPLICATION_READ + "')")
    @Operation(summary = "Get one interview")
    public InterviewDtos.Response get(@PathVariable Long interviewId) {
        return interviewService.get(interviewId);
    }

    @PostMapping("/applications/{applicationId}/interviews")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.APPLICATION_MANAGE + "')")
    @Operation(summary = "Schedule an interview",
            description = """
                    Omit `roundNumber` to append the next round.

                    Also moves the application to `INTERVIEW_SCHEDULED`, so there is no need
                    to change the status separately.

                    An address or joining link is required for `IN_PERSON` and `VIDEO` - a
                    candidate cannot attend an interview with no location. `PHONE` is exempt,
                    since their number is already on the record.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Scheduled"),
            @ApiResponse(responseCode = "422",
                    description = "The application is terminal or not yet at SCREENING, the "
                            + "slot is in the past, the round already exists, or a venue is "
                            + "missing (BUSINESS_RULE_VIOLATION)")
    })
    public InterviewDtos.Response schedule(
            @PathVariable Long applicationId,
            @Valid @RequestBody InterviewDtos.Request request,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return interviewService.schedule(
                applicationId, request, caller, RequestContext.from(httpRequest));
    }

    @PutMapping("/interviews/{interviewId}")
    @PreAuthorize("hasAuthority('" + Permissions.APPLICATION_MANAGE + "')")
    @Operation(summary = "Reschedule or correct an interview",
            description = "Refused once a result is recorded - moving the date would make the "
                    + "feedback refer to a slot that never happened.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated interview"),
            @ApiResponse(responseCode = "422",
                    description = "A result is already recorded, or the new slot is in the "
                            + "past (BUSINESS_RULE_VIOLATION)")
    })
    public InterviewDtos.Response update(@PathVariable Long interviewId,
                                         @Valid @RequestBody InterviewDtos.Request request,
                                         @AuthenticationPrincipal AuthenticatedUser caller,
                                         HttpServletRequest httpRequest) {
        return interviewService.update(
                interviewId, request, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/interviews/{interviewId}/result")
    @PreAuthorize("hasAuthority('" + Permissions.APPLICATION_MANAGE + "')")
    @Operation(summary = "Record the outcome of an interview",
            description = """
                    `PASS`, `FAIL` or `NO_SHOW`. Write-once: a recorded outcome cannot be
                    revised, because it is the evidence the candidate was assessed. Schedule
                    another round if the assessment has changed.

                    Set `advanceApplication: true` to move a passed application to `SELECTED`
                    in the same call. It is not the default - passing round one of three is
                    not a decision to hire, and `SELECTED` commits an opening.

                    A `FAIL` or `NO_SHOW` does **not** auto-reject. Rejecting needs a reason
                    and is a person's decision; `ON_HOLD` exists for a good candidate who
                    suits another role.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated interview"),
            @ApiResponse(responseCode = "422",
                    description = "A result is already recorded, the slot is still in the "
                            + "future, or PENDING was sent as an outcome "
                            + "(BUSINESS_RULE_VIOLATION)")
    })
    public InterviewDtos.Response recordResult(
            @PathVariable Long interviewId,
            @Valid @RequestBody InterviewDtos.ResultRequest request,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return interviewService.recordResult(
                interviewId, request, caller, RequestContext.from(httpRequest));
    }

    @DeleteMapping("/interviews/{interviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('" + Permissions.APPLICATION_MANAGE + "')")
    @Operation(summary = "Cancel a scheduled interview",
            description = "Soft delete. Refused once a result is recorded - it happened, and "
                    + "the feedback is the record of it. If this was the only outstanding slot, "
                    + "the application returns to SHORTLISTED.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Cancelled"),
            @ApiResponse(responseCode = "422",
                    description = "A result is already recorded (BUSINESS_RULE_VIOLATION)")
    })
    public void cancel(@PathVariable Long interviewId,
                       @AuthenticationPrincipal AuthenticatedUser caller,
                       HttpServletRequest httpRequest) {
        interviewService.cancel(interviewId, caller, RequestContext.from(httpRequest));
    }
}
