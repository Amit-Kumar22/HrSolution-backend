package com.hrsolution.compliance.controller;

import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.security.Permissions;
import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.compliance.dto.ComplianceDtos;
import com.hrsolution.compliance.service.ComplianceService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(ApiPaths.V1)
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Compliance", description = "Licences, statutory challans, reminders")
public class ComplianceController {

    private final ComplianceService complianceService;

    @GetMapping("/licences")
    @PreAuthorize("hasAuthority('" + Permissions.COMPLIANCE_READ + "')")
    public PageResponse<ComplianceDtos.LicenceResponse> listLicences(
            @RequestParam(required = false) Long clientId,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @ParameterObject @PageableDefault(size = 20, sort = "expiryDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return complianceService.listLicences(clientId, caller, pageable);
    }

    @PostMapping("/licences")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.COMPLIANCE_MANAGE + "')")
    public ComplianceDtos.LicenceResponse createLicence(@Valid @RequestBody ComplianceDtos.LicenceRequest request,
                                                       @AuthenticationPrincipal AuthenticatedUser caller,
                                                       HttpServletRequest httpRequest) {
        return complianceService.createLicence(request, caller, RequestContext.from(httpRequest));
    }

    @PutMapping("/licences/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.COMPLIANCE_MANAGE + "')")
    public ComplianceDtos.LicenceResponse updateLicence(@PathVariable Long id,
                                                       @Valid @RequestBody ComplianceDtos.LicenceRequest request,
                                                       @AuthenticationPrincipal AuthenticatedUser caller,
                                                       HttpServletRequest httpRequest) {
        return complianceService.updateLicence(id, request, caller, RequestContext.from(httpRequest));
    }

    @GetMapping("/compliance/reminders")
    @PreAuthorize("hasAuthority('" + Permissions.COMPLIANCE_READ + "')")
    public List<ComplianceDtos.ReminderResponse> reminders(@RequestParam(defaultValue = "60") int withinDays) {
        return complianceService.reminders(withinDays);
    }

    @PostMapping("/compliance/reminders/generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('" + Permissions.COMPLIANCE_MANAGE + "')")
    public void generate() {
        complianceService.generateReminders();
    }

    @GetMapping("/challans")
    @PreAuthorize("hasAuthority('" + Permissions.COMPLIANCE_READ + "')")
    public PageResponse<ComplianceDtos.ChallanResponse> listChallans(
            @ParameterObject @PageableDefault(size = 20, sort = "year", direction = Sort.Direction.DESC) Pageable pageable) {
        return complianceService.listChallans(pageable);
    }

    @PostMapping("/challans")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.COMPLIANCE_MANAGE + "')")
    public ComplianceDtos.ChallanResponse createChallan(@Valid @RequestBody ComplianceDtos.ChallanRequest request,
                                                       @AuthenticationPrincipal AuthenticatedUser caller,
                                                       HttpServletRequest httpRequest) {
        return complianceService.createChallan(request, caller, RequestContext.from(httpRequest));
    }
}
