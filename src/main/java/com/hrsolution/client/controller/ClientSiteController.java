package com.hrsolution.client.controller;

import com.hrsolution.client.dto.ClientSiteDtos;
import com.hrsolution.client.service.ClientSiteService;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.security.Permissions;
import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.common.web.RequestContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * Client sites - the locations workers are deployed to.
 *
 * <p>A site's state code is not address decoration: it selects the minimum wage,
 * the professional tax slab and the contract labour licence that apply to
 * everyone working there. A client headquartered in Pune can run a plant in
 * Gujarat, and the plant's state is what governs.
 */
@RestController
@RequestMapping(ApiPaths.V1)
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Clients", description = "Client companies, their portal users and approval")
public class ClientSiteController {

    private final ClientSiteService siteService;

    @GetMapping("/clients/{clientId}/sites")
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_READ + "')")
    @Operation(summary = "List a client's sites",
            description = "Active sites first. Ownership-checked, so a client user can only "
                    + "list their own.")
    public List<ClientSiteDtos.Response> list(@PathVariable Long clientId,
                                              @AuthenticationPrincipal AuthenticatedUser caller) {
        return siteService.listForClient(clientId, caller);
    }

    @GetMapping("/sites/{siteId}")
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_READ + "')")
    @Operation(summary = "Get one site")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The site"),
            @ApiResponse(responseCode = "404",
                    description = "No such site, or not yours (RESOURCE_NOT_FOUND)")
    })
    public ClientSiteDtos.Response get(@PathVariable Long siteId,
                                       @AuthenticationPrincipal AuthenticatedUser caller) {
        return siteService.get(siteId, caller);
    }

    @PostMapping("/clients/{clientId}/sites")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_WRITE + "')")
    @Operation(summary = "Add a site to a client",
            description = "Set stateCode to the state the site is IN - it drives minimum wage "
                    + "and professional tax for workers deployed here, and may differ from the "
                    + "client's billing state.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "409",
                    description = "That site code already exists for this client (DUPLICATE_RESOURCE)"),
            @ApiResponse(responseCode = "422",
                    description = "The client is not active (BUSINESS_RULE_VIOLATION)")
    })
    public ClientSiteDtos.Response create(@PathVariable Long clientId,
                                          @Valid @RequestBody ClientSiteDtos.Request request,
                                          @AuthenticationPrincipal AuthenticatedUser caller,
                                          HttpServletRequest httpRequest) {
        return siteService.create(clientId, request, caller, RequestContext.from(httpRequest));
    }

    @PutMapping("/sites/{siteId}")
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_WRITE + "')")
    @Operation(summary = "Update a site",
            description = "Changing the state code changes the applicable minimum wage and PT "
                    + "slab for future payroll; past payroll is unaffected.")
    public ClientSiteDtos.Response update(@PathVariable Long siteId,
                                          @Valid @RequestBody ClientSiteDtos.Request request,
                                          @AuthenticationPrincipal AuthenticatedUser caller,
                                          HttpServletRequest httpRequest) {
        return siteService.update(siteId, request, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/sites/{siteId}/active")
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_WRITE + "')")
    @Operation(summary = "Activate or deactivate a site",
            description = "Deactivating hides it from new deployments while keeping existing "
                    + "deployments, attendance sheets and invoices resolvable.")
    public ClientSiteDtos.Response setActive(@PathVariable Long siteId,
                                             @RequestParam(defaultValue = "true") boolean active,
                                             @AuthenticationPrincipal AuthenticatedUser caller,
                                             HttpServletRequest httpRequest) {
        return siteService.setActive(siteId, active, caller, RequestContext.from(httpRequest));
    }

    @DeleteMapping("/sites/{siteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_WRITE + "')")
    @Operation(summary = "Delete a site",
            description = "Soft delete. Prefer deactivating a site that has ever been used.")
    public void delete(@PathVariable Long siteId,
                       @AuthenticationPrincipal AuthenticatedUser caller,
                       HttpServletRequest httpRequest) {
        siteService.delete(siteId, caller, RequestContext.from(httpRequest));
    }
}
