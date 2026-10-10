package com.hrsolution.client.controller;

import com.hrsolution.client.dto.ClientContractDtos;
import com.hrsolution.client.service.ClientContractService;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.security.Permissions;
import com.hrsolution.common.web.ApiPaths;
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
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;

/**
 * Client contracts - the commercial terms Phase 9 bills against.
 *
 * <p>The lifecycle is {@code DRAFT → ACTIVE → TERMINATED/EXPIRED}, and the
 * transitions are deliberately separate endpoints rather than a status field on
 * the update body: activating is what makes terms binding and freezes them, so
 * it should be an explicit act.
 */
@RestController
@RequestMapping(ApiPaths.V1)
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Contracts", description = "Client contracts and their commercial terms")
public class ClientContractController {

    private final ClientContractService contractService;

    @GetMapping("/clients/{clientId}/contracts")
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_READ + "')")
    @Operation(summary = "List a client's contracts, newest first")
    public List<ClientContractDtos.Response> list(
            @PathVariable Long clientId,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return contractService.listForClient(clientId, caller);
    }

    @GetMapping("/clients/{clientId}/contracts/in-force")
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_READ + "')")
    @Operation(summary = "Find the contract to bill against on a date",
            description = "What Phase 9 will call when computing the service charge. Returns 404 "
                    + "when no active contract covers the date, and fails loudly if more than "
                    + "one does - picking one arbitrarily would be a silent revenue error.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The contract in force"),
            @ApiResponse(responseCode = "404",
                    description = "No active contract covers that date (RESOURCE_NOT_FOUND)"),
            @ApiResponse(responseCode = "422",
                    description = "More than one active contract covers it (BUSINESS_RULE_VIOLATION)")
    })
    public ClientContractDtos.Response inForce(
            @PathVariable Long clientId,
            @Parameter(description = "Defaults to today")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate onDate,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return contractService.findInForceOn(clientId, onDate, caller);
    }

    @GetMapping("/contracts/{contractId}")
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_READ + "')")
    @Operation(summary = "Get one contract")
    public ClientContractDtos.Response get(@PathVariable Long contractId,
                                           @AuthenticationPrincipal AuthenticatedUser caller) {
        return contractService.get(contractId, caller);
    }

    @PostMapping("/clients/{clientId}/contracts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_WRITE + "')")
    @Operation(summary = "Draft a contract",
            description = "Created DRAFT. Activating is separate, because activation is what "
                    + "makes the terms binding and freezes them against edits.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Drafted"),
            @ApiResponse(responseCode = "400",
                    description = "A percentage service charge above 100% (VALIDATION_FAILED)"),
            @ApiResponse(responseCode = "409",
                    description = "Contract number already used (DUPLICATE_RESOURCE)")
    })
    public ClientContractDtos.Response create(
            @PathVariable Long clientId,
            @Valid @RequestBody ClientContractDtos.Request request,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return contractService.create(clientId, request, caller, RequestContext.from(httpRequest));
    }

    @PutMapping("/contracts/{contractId}")
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_WRITE + "')")
    @Operation(summary = "Update a draft contract",
            description = "Only while DRAFT. Once active, the terms may have been billed "
                    + "against, and changing them would make an issued invoice unreproducible.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated"),
            @ApiResponse(responseCode = "422",
                    description = "The contract is no longer a draft (BUSINESS_RULE_VIOLATION)")
    })
    public ClientContractDtos.Response update(
            @PathVariable Long contractId,
            @Valid @RequestBody ClientContractDtos.Request request,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return contractService.update(contractId, request, caller,
                RequestContext.from(httpRequest));
    }

    @PatchMapping("/contracts/{contractId}/activate")
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_WRITE + "')")
    @Operation(summary = "Activate a draft contract",
            description = "Refused if another active contract for the same client covers any of "
                    + "the same days - two would make the service charge on an invoice "
                    + "ambiguous. Note an open-ended contract blocks every later one until it is "
                    + "given an end date or terminated.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Activated"),
            @ApiResponse(responseCode = "422",
                    description = "Not a draft, or dates overlap a live contract (BUSINESS_RULE_VIOLATION)")
    })
    public ClientContractDtos.Response activate(
            @PathVariable Long contractId,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return contractService.activate(contractId, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/contracts/{contractId}/terminate")
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_WRITE + "')")
    @Operation(summary = "Terminate an active contract",
            description = "The termination date becomes the effective end date, so billing after "
                    + "it finds no contract in force rather than quietly reusing the old terms.")
    public ClientContractDtos.Response terminate(
            @PathVariable Long contractId,
            @Valid @RequestBody ClientContractDtos.TerminateRequest request,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return contractService.terminate(contractId, request, caller,
                RequestContext.from(httpRequest));
    }

    @PostMapping(value = "/contracts/{contractId}/document",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_WRITE + "')")
    @Operation(summary = "Attach the signed contract document",
            description = "PDF or image, up to 10 MB, validated by content rather than file "
                    + "name. Stored privately - downloadable only through the authenticated "
                    + "document endpoint.")
    public ClientContractDtos.Response uploadDocument(
            @PathVariable Long contractId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return contractService.uploadDocument(contractId, file, caller,
                RequestContext.from(httpRequest));
    }

    @DeleteMapping("/contracts/{contractId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_WRITE + "')")
    @Operation(summary = "Delete a contract",
            description = "Soft delete, and refused while ACTIVE - a live contract may be "
                    + "carrying this month's billing terms.")
    public void delete(@PathVariable Long contractId,
                       @AuthenticationPrincipal AuthenticatedUser caller,
                       HttpServletRequest httpRequest) {
        contractService.delete(contractId, caller, RequestContext.from(httpRequest));
    }
}
