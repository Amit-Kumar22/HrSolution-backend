package com.hrsolution.deployment.controller;

import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.security.Permissions;
import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.deployment.dto.DeploymentDtos;
import com.hrsolution.deployment.entity.DeploymentStatus;
import com.hrsolution.deployment.service.DeploymentService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Deployments - posting workers to client sites.
 *
 * <p>Two things to know before building against this.
 *
 * <p><strong>A worker can hold only one active posting.</strong> Creating one
 * that overlaps an existing posting is a 422 naming the conflict. To move
 * somebody, use {@code PATCH /{id}/transfer} rather than ending and recreating
 * by hand - doing it in two calls is how a worker ends up double-counted for a
 * day, or a client short for two.
 *
 * <p><strong>The wage and billing rate come from the rate card.</strong> Leave
 * them out of the request. They are snapshotted onto the posting, so a later
 * rate revision does not restate what it costs.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/deployments")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Deployments", description = "Workers posted to client sites")
public class DeploymentController {

    private final DeploymentService deploymentService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.DEPLOYMENT_READ + "')")
    @Operation(summary = "List deployments",
            description = "A client user always sees only their own, whatever clientId is sent.")
    public PageResponse<DeploymentDtos.SummaryResponse> list(
            @Parameter(description = "Ignored for a client user, who always gets their own")
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) Long workerId,
            @RequestParam(required = false) Long requisitionId,
            @RequestParam(required = false) DeploymentStatus status,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @ParameterObject
            @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return deploymentService.list(
                clientId, siteId, workerId, requisitionId, status, caller, pageable);
    }

    @GetMapping("/{deploymentId}")
    @PreAuthorize("hasAuthority('" + Permissions.DEPLOYMENT_READ + "')")
    @Operation(summary = "Get one deployment")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The deployment"),
            @ApiResponse(responseCode = "404",
                    description = "No such deployment, or not yours (RESOURCE_NOT_FOUND)")
    })
    public DeploymentDtos.Response get(@PathVariable Long deploymentId,
                                       @AuthenticationPrincipal AuthenticatedUser caller) {
        return deploymentService.get(deploymentId, caller);
    }

    @GetMapping("/at-site/{siteId}")
    @PreAuthorize("hasAuthority('" + Permissions.DEPLOYMENT_READ + "')")
    @Operation(summary = "Everyone posted at a site on a date",
            description = """
                    The basis of Phase 7's attendance sheet, and the roster a client's own
                    portal shows.

                    Returns the **client-facing** shape: no wage, no billing rate, no margin. A
                    client is entitled to know who is on their premises and in what role; the
                    commercial terms are on their contract and rate card, and a per-person wage
                    here would expose how the headline rate is composed.

                    `policeVerified` is on each row because the principal employer is liable
                    for checking it too.""")
    public List<DeploymentDtos.ClientResponse> listAtSite(
            @PathVariable Long siteId,
            @Parameter(description = "Defaults to today")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate onDate,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return deploymentService.listAtSiteOn(siteId, onDate, caller);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.DEPLOYMENT_MANAGE + "')")
    @Operation(summary = "Post a worker to a site",
            description = """
                    The client is taken from the site, so there is no `clientId` to send - and
                    no way to post a worker to a site belonging to a different client.

                    Leave `monthlyWage` and `billingRate` out: they are resolved from the rate
                    card applicable on `startDate` and snapshotted. Supply **both** only to
                    override deliberately.

                    Leaving `endDate` null makes the posting open-ended, which blocks the worker
                    from being posted anywhere else until it is ended.

                    Supplying `requisitionId` is what advances that requisition's fulfilment
                    from `IN_PROGRESS` to `PARTIALLY_FULFILLED` to `FULFILLED`.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Posted"),
            @ApiResponse(responseCode = "422",
                    description = """
                            Several distinct cases, all BUSINESS_RULE_VIOLATION - read `detail`:
                            the worker already holds an overlapping posting; they have left or
                            are on leave; a security role without a clear police verification;
                            no rate card covers the category on that date; the requisition is
                            for another site or not yet approved; the site or client is
                            inactive.""")
    })
    public DeploymentDtos.Response create(@Valid @RequestBody DeploymentDtos.Request request,
                                          @AuthenticationPrincipal AuthenticatedUser caller,
                                          HttpServletRequest httpRequest) {
        return deploymentService.create(request, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/{deploymentId}/end")
    @PreAuthorize("hasAuthority('" + Permissions.DEPLOYMENT_MANAGE + "')")
    @Operation(summary = "End a posting",
            description = """
                    `RELEASED` when pulled out early, `COMPLETED` when it ran its course.

                    Sets the end date as well as the status - a terminal status with an open end
                    date would still block the worker from being posted elsewhere.

                    The worker's status and the requisition's fulfilment both follow
                    automatically, so refetch them if they are on screen.""")
    public DeploymentDtos.Response end(@PathVariable Long deploymentId,
                                       @Valid @RequestBody DeploymentDtos.EndRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser caller,
                                       HttpServletRequest httpRequest) {
        return deploymentService.end(
                deploymentId, request, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/{deploymentId}/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.DEPLOYMENT_MANAGE + "')")
    @Operation(summary = "Move a worker to another site",
            description = """
                    **Use this rather than ending and recreating.** The current posting ends the
                    day before `effectiveFrom` and the new one starts on it, in one transaction
                    - so there is neither a gap nor an overlap. Doing it by hand is how a worker
                    gets double-counted for a day, or a client is left short for two.

                    The rate is re-resolved rather than carried over: the destination may be a
                    different client, in a different state, on a different rate card.

                    Returns the **new** deployment.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The new deployment"),
            @ApiResponse(responseCode = "422",
                    description = "Already ended, the transfer date is not after the start, "
                            + "the destination is the same site or inactive, or the worker is "
                            + "not deployable there (BUSINESS_RULE_VIOLATION)")
    })
    public DeploymentDtos.Response transfer(
            @PathVariable Long deploymentId,
            @Valid @RequestBody DeploymentDtos.TransferRequest request,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return deploymentService.transfer(
                deploymentId, request, caller, RequestContext.from(httpRequest));
    }
}
