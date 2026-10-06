package com.hrsolution.settings.controller;

import com.hrsolution.common.security.Permissions;
import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.settings.dto.CompanySettingsResponse;
import com.hrsolution.settings.dto.UpdateCompanySettingsRequest;
import com.hrsolution.settings.service.CompanySettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Company profile endpoints.
 *
 * <p>Phase 2 adds {@code @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")} to
 * {@link #update}; the read stays available to every signed-in user, since the
 * portal header and invoice previews need it.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/settings/company")
@RequiredArgsConstructor
@Tag(name = "Settings", description = "Company profile and system configuration")
public class CompanySettingsController {

    private final CompanySettingsService companySettingsService;

    @GetMapping
    @Operation(
            summary = "Get the company profile",
            description = "Returns the single company profile row. Cached server-side; "
                    + "the response changes only when the profile is edited.")
    @ApiResponse(responseCode = "200", description = "The current company profile")
    public CompanySettingsResponse get() {
        return companySettingsService.get();
    }

    @PutMapping
    @PreAuthorize("hasAuthority('" + Permissions.SETTINGS_MANAGE + "')")
    @Operation(
            summary = "Update the company profile",
            description = "Full replacement: send every field, as omitted fields are cleared. "
                    + "The logo is managed by its own upload endpoint and is unaffected.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated"),
            @ApiResponse(responseCode = "400",
                    description = "Validation failed - see the errors array for the offending fields "
                            + "(errorCode VALIDATION_FAILED)"),
            @ApiResponse(responseCode = "409",
                    description = "Someone else saved first (errorCode CONCURRENT_MODIFICATION)")
    })
    public CompanySettingsResponse update(@Valid @RequestBody UpdateCompanySettingsRequest request) {
        return companySettingsService.update(request);
    }
}
