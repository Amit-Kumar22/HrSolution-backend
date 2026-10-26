package com.hrsolution.billing.controller;

import com.hrsolution.billing.dto.BillingDtos;
import com.hrsolution.billing.entity.InvoiceStatus;
import com.hrsolution.billing.service.BillingService;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.security.Permissions;
import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.V1 + "/invoices")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Billing", description = "Invoices, payments and credit notes")
public class BillingController {

    private final BillingService billingService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.INVOICE_READ + "')")
    public PageResponse<BillingDtos.SummaryResponse> list(
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) InvoiceStatus status,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @ParameterObject
            @PageableDefault(size = 20, sort = "invoiceDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return billingService.list(clientId, status, caller, pageable);
    }

    @GetMapping("/{invoiceId}")
    @PreAuthorize("hasAuthority('" + Permissions.INVOICE_READ + "')")
    public BillingDtos.Response get(@PathVariable Long invoiceId,
                                    @AuthenticationPrincipal AuthenticatedUser caller) {
        return billingService.get(invoiceId, caller);
    }

    @PostMapping("/from-payroll")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.INVOICE_MANAGE + "')")
    @Operation(summary = "Generate invoice from a processed/approved payroll run")
    public BillingDtos.Response generateFromPayroll(@Valid @RequestBody BillingDtos.GenerateFromPayrollRequest request,
                                                    @AuthenticationPrincipal AuthenticatedUser caller,
                                                    HttpServletRequest httpRequest) {
        return billingService.generateFromPayroll(request, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/{invoiceId}/issue")
    @PreAuthorize("hasAuthority('" + Permissions.INVOICE_MANAGE + "')")
    public BillingDtos.Response issue(@PathVariable Long invoiceId,
                                      @AuthenticationPrincipal AuthenticatedUser caller,
                                      HttpServletRequest request) {
        return billingService.issue(invoiceId, caller, RequestContext.from(request));
    }

    @PostMapping("/{invoiceId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.PAYMENT_MANAGE + "')")
    public BillingDtos.Response recordPayment(@PathVariable Long invoiceId,
                                              @Valid @RequestBody BillingDtos.PaymentRequest request,
                                              @AuthenticationPrincipal AuthenticatedUser caller,
                                              HttpServletRequest httpRequest) {
        return billingService.recordPayment(invoiceId, request, caller, RequestContext.from(httpRequest));
    }

    @PostMapping("/{invoiceId}/credit-notes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.INVOICE_MANAGE + "')")
    public BillingDtos.CreditNoteResponse createCreditNote(@PathVariable Long invoiceId,
                                                           @Valid @RequestBody BillingDtos.CreditNoteRequest request,
                                                           @AuthenticationPrincipal AuthenticatedUser caller,
                                                           HttpServletRequest httpRequest) {
        return billingService.createCreditNote(invoiceId, request, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/{invoiceId}/cancel")
    @PreAuthorize("hasAuthority('" + Permissions.INVOICE_MANAGE + "')")
    public void cancel(@PathVariable Long invoiceId,
                       @RequestParam String reason,
                       @AuthenticationPrincipal AuthenticatedUser caller,
                       HttpServletRequest request) {
        billingService.cancel(invoiceId, reason, caller, RequestContext.from(request));
    }
}
