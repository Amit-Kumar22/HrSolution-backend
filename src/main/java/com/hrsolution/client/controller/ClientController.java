package com.hrsolution.client.controller;

import com.hrsolution.client.dto.ClientDtos;
import com.hrsolution.client.entity.ClientStatus;
import com.hrsolution.client.service.ClientService;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.security.Permissions;
import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
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
 * Client companies and their portal users.
 *
 * <p><strong>Every endpoint taking a client id is also ownership-checked.</strong>
 * {@code CLIENT_READ} lets a client user call these - which is intended, since
 * they need to see their own record - but client ids are sequential integers, so
 * {@code ClientAccessGuard} verifies the id against the caller's own link. A
 * client user reaching for another client gets 404, not 403: a 403 would confirm
 * the record exists and turn the id space into a customer list.
 *
 * <p>Client users should prefer {@code GET /clients/me}, which takes no id at
 * all and so cannot be pointed at anyone else.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/clients")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Clients", description = "Client companies, their portal users and approval")
public class ClientController {

    private final ClientService clientService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_READ + "')")
    @Operation(summary = "List clients",
            description = "Staff see every client. A client user sees only their own, whatever "
                    + "the clientId filter says - a forged filter cannot widen the result set.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A page of clients"),
            @ApiResponse(responseCode = "403", description = "Missing CLIENT_READ (ACCESS_DENIED)")
    })
    public PageResponse<ClientDtos.SummaryResponse> list(
            @Parameter(description = "Free text across legal name, trade name, GSTIN and city")
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ClientStatus status,
            @Parameter(description = "Two-digit GST state code")
            @RequestParam(required = false) String stateCode,
            @Parameter(description = "Ignored for client users, who always see only their own")
            @RequestParam(required = false) Long clientId,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @ParameterObject @PageableDefault(size = 20, sort = "legalName") Pageable pageable) {
        return clientService.list(search, status, stateCode, clientId, caller, pageable);
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_READ + "')")
    @Operation(summary = "Get the calling client user's own company",
            description = "Takes no id, so it cannot be pointed at another client. The preferred "
                    + "endpoint for the client portal.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Your company"),
            @ApiResponse(responseCode = "422",
                    description = "Your account is not linked to a client (BUSINESS_RULE_VIOLATION)")
    })
    public ClientDtos.Response getOwn(@AuthenticationPrincipal AuthenticatedUser caller) {
        return clientService.getOwnClient(caller);
    }

    @GetMapping("/{clientId}")
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_READ + "')")
    @Operation(summary = "Get one client",
            description = "Includes the GST treatment derived by comparing the client's billing "
                    + "state code with the company's own.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The client"),
            @ApiResponse(responseCode = "404",
                    description = "No such client, or not yours (RESOURCE_NOT_FOUND)")
    })
    public ClientDtos.Response get(@PathVariable Long clientId,
                                   @AuthenticationPrincipal AuthenticatedUser caller) {
        return clientService.get(clientId, caller);
    }

    @GetMapping("/{clientId}/detail")
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_READ + "')")
    @Operation(summary = "Get a client with its users, sites and contracts",
            description = "One call for the client overview screen, instead of four.")
    public ClientDtos.DetailResponse getDetail(@PathVariable Long clientId,
                                               @AuthenticationPrincipal AuthenticatedUser caller) {
        return clientService.getDetail(clientId, caller);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_WRITE + "')")
    @Operation(summary = "Create a client",
            description = "Created ACTIVE, since staff adding a client directly have already "
                    + "vetted it - unlike a self-registration, which arrives PENDING_APPROVAL. "
                    + "GSTIN is optional here but required before Phase 9 can invoice.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "409",
                    description = "GSTIN or legal name already used (DUPLICATE_RESOURCE)")
    })
    public ClientDtos.Response create(@Valid @RequestBody ClientDtos.Request request,
                                      @AuthenticationPrincipal AuthenticatedUser caller,
                                      HttpServletRequest httpRequest) {
        return clientService.create(request, caller, RequestContext.from(httpRequest));
    }

    @PutMapping("/{clientId}")
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_WRITE + "')")
    @Operation(summary = "Update a client",
            description = "Status, industry and onboarding date have their own endpoints so each "
                    + "change is audited distinctly. Changing the billing state code flips GST "
                    + "treatment for future invoices and is logged explicitly.")
    public ClientDtos.Response update(@PathVariable Long clientId,
                                      @Valid @RequestBody ClientDtos.Request request,
                                      @AuthenticationPrincipal AuthenticatedUser caller,
                                      HttpServletRequest httpRequest) {
        return clientService.update(clientId, request, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/{clientId}/status")
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_WRITE + "')")
    @Operation(summary = "Change a client's status",
            description = "SUSPENDED stops new work but still permits invoicing and collection "
                    + "for work already done. A client cannot be moved back to PENDING_APPROVAL.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status changed"),
            @ApiResponse(responseCode = "422",
                    description = "Already in that status, or an illegal transition (BUSINESS_RULE_VIOLATION)")
    })
    public ClientDtos.Response changeStatus(
            @PathVariable Long clientId,
            @Valid @RequestBody ClientDtos.StatusChangeRequest request,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return clientService.changeStatus(clientId, request, caller,
                RequestContext.from(httpRequest));
    }

    @DeleteMapping("/{clientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_WRITE + "')")
    @Operation(summary = "Delete a client",
            description = "Soft delete - invoices, payroll and statutory returns reference "
                    + "clients for years. Refused while an active contract exists.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted"),
            @ApiResponse(responseCode = "422",
                    description = "An active contract still exists (BUSINESS_RULE_VIOLATION)")
    })
    public void delete(@PathVariable Long clientId,
                       @AuthenticationPrincipal AuthenticatedUser caller,
                       HttpServletRequest httpRequest) {
        clientService.delete(clientId, caller, RequestContext.from(httpRequest));
    }

    // ==================================================================
    // Portal users
    // ==================================================================

    @GetMapping("/{clientId}/users")
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_READ + "')")
    @Operation(summary = "List the people at this client who can sign in")
    public List<ClientDtos.UserResponse> listUsers(
            @PathVariable Long clientId,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return clientService.listUsers(clientId, caller);
    }

    @PostMapping("/{clientId}/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_WRITE + "')")
    @Operation(summary = "Link an existing CLIENT-role user to this client",
            description = "A login belongs to exactly one client, so a user already linked "
                    + "elsewhere is rejected rather than shared.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Linked"),
            @ApiResponse(responseCode = "400",
                    description = "Unknown user, or one without the CLIENT role (VALIDATION_FAILED)"),
            @ApiResponse(responseCode = "409",
                    description = "That user is already linked to a client (DUPLICATE_RESOURCE)")
    })
    public ClientDtos.UserResponse linkUser(
            @PathVariable Long clientId,
            @Valid @RequestBody ClientDtos.LinkUserRequest request,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return clientService.linkUser(clientId, request, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/{clientId}/users/{clientUserId}/primary-contact")
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_WRITE + "')")
    @Operation(summary = "Nominate the primary contact",
            description = "Clears the flag from whoever held it. At most one per client.")
    public ClientDtos.UserResponse setPrimaryContact(
            @PathVariable Long clientId,
            @PathVariable Long clientUserId,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return clientService.setPrimaryContact(clientId, clientUserId, caller,
                RequestContext.from(httpRequest));
    }

    @PatchMapping("/{clientId}/users/{clientUserId}/active")
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_WRITE + "')")
    @Operation(summary = "Revoke or restore a person's access",
            description = "Keeps the link so the audit trail still explains what they could see. "
                    + "The primary contact cannot be revoked until someone else is nominated.")
    public ClientDtos.UserResponse setUserActive(
            @PathVariable Long clientId,
            @PathVariable Long clientUserId,
            @RequestParam(defaultValue = "true") boolean active,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return clientService.setUserActive(clientId, clientUserId, active, caller,
                RequestContext.from(httpRequest));
    }

    // ==================================================================
    // Registration approval
    // ==================================================================

    @PostMapping("/approve-registration/{userId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_APPROVE + "')")
    @Operation(summary = "Approve a self-registered client and create its client record",
            description = """
                    Completes the hand-off Phase 2 could only half-do. In one transaction this \
                    creates the clients row, links the registrant's login to it as primary \
                    contact, and activates the account.

                    One transaction on purpose: a user activated without a client link can sign \
                    in but see nothing, and a client created without an activated user is a \
                    record nobody can reach.

                    The legal name defaults to what the registrant typed but should be corrected \
                    to the registered name before approving - their self-declared version is \
                    kept as the trade name.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Client created and login activated"),
            @ApiResponse(responseCode = "422",
                    description = "Not a client registration, or already approved (BUSINESS_RULE_VIOLATION)"),
            @ApiResponse(responseCode = "409",
                    description = "GSTIN or legal name already used (DUPLICATE_RESOURCE)")
    })
    public ClientDtos.Response approveRegistration(
            @PathVariable Long userId,
            @Valid @RequestBody ClientDtos.ApproveRegistrationRequest request,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return clientService.approveRegistration(userId, request, caller,
                RequestContext.from(httpRequest));
    }
}
