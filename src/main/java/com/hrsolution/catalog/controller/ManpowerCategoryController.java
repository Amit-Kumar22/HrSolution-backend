package com.hrsolution.catalog.controller;

import com.hrsolution.catalog.dto.ManpowerCategoryDtos;
import com.hrsolution.catalog.service.ManpowerCategoryService;
import com.hrsolution.common.security.Permissions;
import com.hrsolution.common.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The manpower category master, for staff.
 *
 * <p>The public, trimmed list that feeds the enquiry form's dropdown lives on
 * {@code PublicSiteController} instead.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/manpower-categories")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Master data", description = "Manpower categories and skill levels")
public class ManpowerCategoryController {

    private final ManpowerCategoryService categoryService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List all categories",
            description = "Includes deactivated ones. Any signed-in user may read this - "
                    + "requisition, deployment and payroll screens all need the list.")
    public List<ManpowerCategoryDtos.Response> list() {
        return categoryService.listAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get one category")
    public ManpowerCategoryDtos.Response get(@PathVariable Long id) {
        return categoryService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.CONTENT_MANAGE + "')")
    @Operation(summary = "Create a category",
            description = "The skill level selects which state minimum wage applies, so it is a "
                    + "payroll decision rather than a label.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "409", description = "Code or name already used (DUPLICATE_RESOURCE)")
    })
    public ManpowerCategoryDtos.Response create(
            @Valid @RequestBody ManpowerCategoryDtos.Request request) {
        return categoryService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.CONTENT_MANAGE + "')")
    @Operation(summary = "Update a category",
            description = "The code cannot be changed - it is the stable identifier used by Excel "
                    + "imports and exports. Deactivate and recreate instead.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated"),
            @ApiResponse(responseCode = "422",
                    description = "Attempted to change the code (BUSINESS_RULE_VIOLATION)")
    })
    public ManpowerCategoryDtos.Response update(
            @PathVariable Long id, @Valid @RequestBody ManpowerCategoryDtos.Request request) {
        return categoryService.update(id, request);
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('" + Permissions.CONTENT_MANAGE + "')")
    @Operation(summary = "Deactivate a category",
            description = "There is no delete: enquiries, requisitions, deployments and payroll "
                    + "all reference categories, so removing one would orphan history. "
                    + "Deactivating hides it from new dropdowns only.")
    public ManpowerCategoryDtos.Response deactivate(@PathVariable Long id) {
        return categoryService.deactivate(id);
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('" + Permissions.CONTENT_MANAGE + "')")
    @Operation(summary = "Reactivate a category")
    public ManpowerCategoryDtos.Response activate(@PathVariable Long id) {
        return categoryService.activate(id);
    }
}
