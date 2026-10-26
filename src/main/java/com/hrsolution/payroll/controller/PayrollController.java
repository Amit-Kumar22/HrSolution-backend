package com.hrsolution.payroll.controller;

import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.security.Permissions;
import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.payroll.dto.PayrollDtos;
import com.hrsolution.payroll.entity.PayrollRunStatus;
import com.hrsolution.payroll.service.PayrollService;
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
@RequestMapping(ApiPaths.V1 + "/payroll")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Payroll", description = "Payroll runs, payslips and statutory components")
public class PayrollController {

    private final PayrollService payrollService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.PAYROLL_READ + "')")
    public PageResponse<PayrollDtos.SummaryResponse> list(
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) PayrollRunStatus status,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @ParameterObject
            @PageableDefault(size = 20, sort = "year", direction = Sort.Direction.DESC) Pageable pageable) {
        return payrollService.list(clientId, month, year, status, caller, pageable);
    }

    @GetMapping("/{runId}")
    @PreAuthorize("hasAuthority('" + Permissions.PAYROLL_READ + "')")
    public PayrollDtos.Response get(@PathVariable Long runId,
                                    @AuthenticationPrincipal AuthenticatedUser caller) {
        return payrollService.get(runId, caller);
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.PAYROLL_PROCESS + "')")
    @Operation(summary = "Generate payroll from locked attendance sheets")
    public PayrollDtos.Response generate(@Valid @RequestBody PayrollDtos.GenerateRequest request,
                                          @AuthenticationPrincipal AuthenticatedUser caller,
                                          HttpServletRequest httpRequest) {
        return payrollService.generate(request, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/{runId}/process")
    @PreAuthorize("hasAuthority('" + Permissions.PAYROLL_PROCESS + "')")
    public PayrollDtos.Response process(@PathVariable Long runId,
                                        @AuthenticationPrincipal AuthenticatedUser caller,
                                        HttpServletRequest request) {
        return payrollService.process(runId, caller, RequestContext.from(request));
    }

    @PatchMapping("/{runId}/approve")
    @PreAuthorize("hasAuthority('" + Permissions.PAYROLL_PROCESS + "')")
    public PayrollDtos.Response approve(@PathVariable Long runId,
                                        @AuthenticationPrincipal AuthenticatedUser caller,
                                        HttpServletRequest request) {
        return payrollService.approve(runId, caller, RequestContext.from(request));
    }

    @PatchMapping("/{runId}/pay")
    @PreAuthorize("hasAuthority('" + Permissions.PAYROLL_PROCESS + "')")
    public PayrollDtos.Response markPaid(@PathVariable Long runId,
                                         @AuthenticationPrincipal AuthenticatedUser caller,
                                         HttpServletRequest request) {
        return payrollService.markPaid(runId, caller, RequestContext.from(request));
    }

    @PatchMapping("/{runId}/lock")
    @PreAuthorize("hasAuthority('" + Permissions.PAYROLL_PROCESS + "')")
    public PayrollDtos.Response lock(@PathVariable Long runId,
                                     @AuthenticationPrincipal AuthenticatedUser caller,
                                     HttpServletRequest request) {
        return payrollService.lock(runId, caller, RequestContext.from(request));
    }
}
