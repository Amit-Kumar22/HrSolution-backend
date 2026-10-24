package com.hrsolution.requisition.controller;

import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.security.Permissions;
import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.requisition.dto.RequisitionDtos;
import com.hrsolution.requisition.entity.RequisitionStatus;
import com.hrsolution.requisition.service.RequisitionService;
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

import java.util.List;

/**
 * Requisitions - a client's request for workers.
 *
 * <p><strong>Shared between staff and the client portal.</strong> Unusually for
 * this API, a {@code CLIENT}-role user can call most of these: they hold
 * {@code REQUISITION_CREATE} and {@code REQUISITION_READ}, because raising a
 * requirement is the client's job. Every endpoint resolves the caller's scope
 * through {@code ClientAccessGuard}, so a client sees and touches only their
 * own - a forged {@code clientId} is overridden, not honoured.
 *
 * <p>{@code REQUISITION_APPROVE} is held by SUPER_ADMIN, ADMIN and
 * OPERATIONS_MANAGER only. A client can submit and cancel; accepting the
 * commitment is the company's decision.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/requisitions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Requisitions", description = "Client requests for workers")
public class RequisitionController {

    private final RequisitionService requisitionService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.REQUISITION_READ + "')")
    @Operation(summary = "List requisitions",
            description = "A client user always sees only their own, whatever clientId is sent.")
    public PageResponse<RequisitionDtos.SummaryResponse> list(
            @Parameter(description = "Ignored for a client user, who always gets their own")
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) RequisitionStatus status,
            @RequestParam(required = false) Long categoryId,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @ParameterObject
            @PageableDefault(size = 20, sort = "requiredFrom", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return requisitionService.list(clientId, siteId, status, categoryId, caller, pageable);
    }

    @GetMapping("/urgent")
    @PreAuthorize("hasAuthority('" + Permissions.REQUISITION_READ + "')")
    @Operation(summary = "Committed requirements starting soon that are not yet filled",
            description = "The one operations screen that stops a client being let down on a "
                    + "start date. Put it on the landing page.")
    public List<RequisitionDtos.SummaryResponse> listUrgent(
            @Parameter(description = "How far ahead to look")
            @RequestParam(defaultValue = "7") int withinDays) {
        return requisitionService.listUrgent(withinDays);
    }

    @GetMapping("/{requisitionId}")
    @PreAuthorize("hasAuthority('" + Permissions.REQUISITION_READ + "')")
    @Operation(summary = "Get one requisition")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The requisition"),
            @ApiResponse(responseCode = "404",
                    description = "No such requisition, or not yours (RESOURCE_NOT_FOUND)")
    })
    public RequisitionDtos.Response get(@PathVariable Long requisitionId,
                                        @AuthenticationPrincipal AuthenticatedUser caller) {
        return requisitionService.get(requisitionId, caller);
    }

    @GetMapping("/{requisitionId}/detail")
    @PreAuthorize("hasAuthority('" + Permissions.REQUISITION_READ + "')")
    @Operation(summary = "Get a requisition with its approval trail",
            description = "The history is oldest first; its first entry has a null fromStatus "
                    + "and records the requisition being raised.")
    public RequisitionDtos.DetailResponse getDetail(
            @PathVariable Long requisitionId,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return requisitionService.getDetail(requisitionId, caller);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.REQUISITION_CREATE + "')")
    @Operation(summary = "Raise a requisition",
            description = """
                    Always created as `DRAFT`; submitting is a separate call, so a requirement
                    cannot reach approval before whoever raised it has read it back.

                    A client user's own company is used and `clientId` is ignored. Staff must
                    supply `clientId`.

                    `siteId` is mandatory - the site's state decides the minimum wage and the
                    professional tax slab, so a requisition without one cannot be costed.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created, as a DRAFT"),
            @ApiResponse(responseCode = "422",
                    description = "The site belongs to another client, is inactive, or the "
                            + "dates are inverted (BUSINESS_RULE_VIOLATION)")
    })
    public RequisitionDtos.Response create(
            @Parameter(description = "Required for staff; ignored for a client user")
            @RequestParam(required = false) Long clientId,
            @Valid @RequestBody RequisitionDtos.Request request,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return requisitionService.create(
                clientId, request, caller, RequestContext.from(httpRequest));
    }

    @PutMapping("/{requisitionId}")
    @PreAuthorize("hasAuthority('" + Permissions.REQUISITION_CREATE + "')")
    @Operation(summary = "Amend a requisition",
            description = "A client user can only amend their own while it is still a DRAFT. "
                    + "Staff may amend a submitted or approved one - a client ringing up to "
                    + "change 20 guards to 25 is routine - but not once it is terminal.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated requisition"),
            @ApiResponse(responseCode = "422",
                    description = "Already submitted (client) or terminal "
                            + "(BUSINESS_RULE_VIOLATION)")
    })
    public RequisitionDtos.Response update(@PathVariable Long requisitionId,
                                           @Valid @RequestBody RequisitionDtos.Request request,
                                           @AuthenticationPrincipal AuthenticatedUser caller,
                                           HttpServletRequest httpRequest) {
        return requisitionService.update(
                requisitionId, request, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/{requisitionId}/status")
    @PreAuthorize("hasAuthority('" + Permissions.REQUISITION_CREATE + "')")
    @Operation(summary = "Submit or cancel a requisition",
            description = """
                    For the moves a client may make: `DRAFT` to `SUBMITTED`, or anything
                    pre-terminal to `CANCELLED`.

                    Approval and closure are refused here even for staff holding only
                    `REQUISITION_CREATE` - use `PATCH /{id}/approval`, which checks
                    `REQUISITION_APPROVE`.

                    Only the moves in `allowedNextStatuses` are accepted.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated requisition"),
            @ApiResponse(responseCode = "422",
                    description = "Illegal transition, or a client attempting to approve "
                            + "(BUSINESS_RULE_VIOLATION)")
    })
    public RequisitionDtos.Response changeStatus(
            @PathVariable Long requisitionId,
            @Valid @RequestBody RequisitionDtos.StatusChangeRequest request,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return requisitionService.changeStatus(
                requisitionId, request, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/{requisitionId}/approval")
    @PreAuthorize("hasAuthority('" + Permissions.REQUISITION_APPROVE + "')")
    @Operation(summary = "Approve, reject or close a requisition",
            description = """
                    The company's decision, so it needs `REQUISITION_APPROVE` - which no client
                    role holds.

                    `APPROVED` commits to supplying the workers and is what allows deployments
                    against it. `REJECTED` requires `remarks`: the client will be told it was
                    refused and will ask why.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated requisition"),
            @ApiResponse(responseCode = "422",
                    description = "Illegal transition, or a rejection with no reason "
                            + "(BUSINESS_RULE_VIOLATION)")
    })
    public RequisitionDtos.Response decide(
            @PathVariable Long requisitionId,
            @Valid @RequestBody RequisitionDtos.StatusChangeRequest request,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return requisitionService.changeStatus(
                requisitionId, request, caller, RequestContext.from(httpRequest));
    }

    @DeleteMapping("/{requisitionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('" + Permissions.REQUISITION_APPROVE + "')")
    @Operation(summary = "Delete a requisition",
            description = "Soft delete, and refused once workers are deployed against it. "
                    + "Cancel or close it instead - that keeps the record of what was asked for.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted"),
            @ApiResponse(responseCode = "422",
                    description = "Workers are deployed against it (BUSINESS_RULE_VIOLATION)")
    })
    public void delete(@PathVariable Long requisitionId,
                       @AuthenticationPrincipal AuthenticatedUser caller,
                       HttpServletRequest httpRequest) {
        requisitionService.delete(requisitionId, caller, RequestContext.from(httpRequest));
    }
}
