package com.hrsolution.worker.controller;

import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.security.Permissions;
import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.worker.dto.WorkerBankDtos;
import com.hrsolution.worker.service.WorkerBankService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * A worker's bank accounts.
 *
 * <p>Gated on {@code WORKER_SENSITIVE_READ} to read and {@code WORKER_WRITE} to
 * change - a bank account is the thing an attacker most wants to alter, so the
 * read is held tighter than the rest of the worker record.
 *
 * <p><strong>No endpoint returns the account number in full.</strong> Phase 8
 * builds the bank transfer file server-side from the decrypted value; nothing
 * needs to send it to a browser, so nothing does.
 */
@RestController
@RequestMapping(ApiPaths.V1)
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Worker bank accounts", description = "Where wages are paid")
public class WorkerBankController {

    private final WorkerBankService bankService;

    @GetMapping("/workers/{workerId}/bank-accounts")
    @PreAuthorize("hasAuthority('" + Permissions.WORKER_SENSITIVE_READ + "')")
    @Operation(summary = "List a worker's bank accounts",
            description = "Primary first. Account numbers are masked.")
    public List<WorkerBankDtos.Response> list(@PathVariable Long workerId) {
        return bankService.listForWorker(workerId);
    }

    @PostMapping("/workers/{workerId}/bank-accounts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.WORKER_WRITE + "')")
    @Operation(summary = "Add a bank account",
            description = """
                    The first account added becomes primary automatically - an account nobody
                    marked is still the only one there is.

                    `accountHolderName` defaults to the worker's own name. Set it when the bank
                    holds a different spelling: a mismatch is what makes a transfer file bounce,
                    and that is discovered on payday.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Added"),
            @ApiResponse(responseCode = "409",
                    description = "That account is already registered for this worker "
                            + "(DUPLICATE_RESOURCE)")
    })
    public WorkerBankDtos.Response add(@PathVariable Long workerId,
                                       @Valid @RequestBody WorkerBankDtos.Request request,
                                       @AuthenticationPrincipal AuthenticatedUser caller,
                                       HttpServletRequest httpRequest) {
        return bankService.add(workerId, request, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/bank-accounts/{accountId}/primary")
    @PreAuthorize("hasAuthority('" + Permissions.WORKER_WRITE + "')")
    @Operation(summary = "Make this the account wages are paid into",
            description = "Demotes the existing primary in the same statement, so there is "
                    + "never a moment with none - a concurrent payroll run reading through "
                    + "that window would find nowhere to pay.")
    public WorkerBankDtos.Response makePrimary(
            @PathVariable Long accountId,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return bankService.makePrimary(accountId, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/bank-accounts/{accountId}/active")
    @PreAuthorize("hasAuthority('" + Permissions.WORKER_WRITE + "')")
    @Operation(summary = "Activate or deactivate an account",
            description = "The primary account cannot be deactivated. Promote another one first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated account"),
            @ApiResponse(responseCode = "422",
                    description = "Deactivating the primary account (BUSINESS_RULE_VIOLATION)")
    })
    public WorkerBankDtos.Response setActive(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "true") boolean active,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return bankService.setActive(accountId, active, caller,
                RequestContext.from(httpRequest));
    }

    @DeleteMapping("/bank-accounts/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('" + Permissions.WORKER_WRITE + "')")
    @Operation(summary = "Remove a bank account",
            description = "Soft delete. The primary account cannot be removed while another "
                    + "exists - promote that one first, so there is always exactly one.")
    public void delete(@PathVariable Long accountId,
                       @AuthenticationPrincipal AuthenticatedUser caller,
                       HttpServletRequest httpRequest) {
        bankService.delete(accountId, caller, RequestContext.from(httpRequest));
    }
}
