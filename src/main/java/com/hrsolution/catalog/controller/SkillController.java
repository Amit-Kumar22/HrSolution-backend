package com.hrsolution.catalog.controller;

import com.hrsolution.catalog.dto.SkillDtos;
import com.hrsolution.catalog.service.SkillService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The skills master, and which skills a manpower category typically requires.
 *
 * <p>The category mapping is advisory. Phase 6 records what each worker actually
 * holds and the deployment screen surfaces the gap, but nothing is blocked for a
 * missing skill - a system that refused a deployment because a certificate had
 * not been scanned yet would simply be worked around.
 */
@RestController
@RequestMapping(ApiPaths.V1)
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Master data", description = "Manpower categories and skill levels")
public class SkillController {

    private final SkillService skillService;

    @GetMapping("/skills")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List skills",
            description = "Any signed-in user may read these - recruitment, worker and "
                    + "deployment screens all need the vocabulary.")
    public List<SkillDtos.Response> list(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return includeInactive ? skillService.listAll() : skillService.listActive();
    }

    @PostMapping("/skills")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.CONTENT_MANAGE + "')")
    @Operation(summary = "Create a skill")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "409", description = "Name already used (DUPLICATE_RESOURCE)")
    })
    public SkillDtos.Response create(@Valid @RequestBody SkillDtos.Request request) {
        return skillService.create(request);
    }

    @PutMapping("/skills/{skillId}")
    @PreAuthorize("hasAuthority('" + Permissions.CONTENT_MANAGE + "')")
    @Operation(summary = "Update a skill")
    public SkillDtos.Response update(@PathVariable Long skillId,
                                     @Valid @RequestBody SkillDtos.Request request) {
        return skillService.update(skillId, request);
    }

    @PatchMapping("/skills/{skillId}/active")
    @PreAuthorize("hasAuthority('" + Permissions.CONTENT_MANAGE + "')")
    @Operation(summary = "Activate or deactivate a skill",
            description = "There is no delete: workers reference skills, and removing one would "
                    + "erase a recorded certification.")
    public SkillDtos.Response setActive(@PathVariable Long skillId,
                                        @RequestParam(defaultValue = "true") boolean active) {
        return skillService.setActive(skillId, active);
    }

    @GetMapping("/manpower-categories/{categoryId}/skills")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List the skills a category typically requires")
    public List<SkillDtos.Response> listForCategory(@PathVariable Long categoryId) {
        return skillService.listForCategory(categoryId);
    }

    @PutMapping("/manpower-categories/{categoryId}/skills")
    @PreAuthorize("hasAuthority('" + Permissions.CONTENT_MANAGE + "')")
    @Operation(summary = "Replace the skills a category requires",
            description = "Full replacement; send an empty array to clear them. Unknown skill "
                    + "ids are rejected rather than silently dropped.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Skills replaced"),
            @ApiResponse(responseCode = "400", description = "Unknown skill id(s) (VALIDATION_FAILED)")
    })
    public List<SkillDtos.Response> assignToCategory(
            @PathVariable Long categoryId,
            @Valid @RequestBody SkillDtos.AssignToCategoryRequest request) {
        return skillService.assignToCategory(categoryId, request);
    }
}
