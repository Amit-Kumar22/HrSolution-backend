package com.hrsolution.user.controller;

import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.security.Permissions;
import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.user.dto.PermissionResponse;
import com.hrsolution.user.dto.RoleResponse;
import com.hrsolution.user.dto.UserAdminRequests;
import com.hrsolution.user.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The role and permission catalogue.
 *
 * <p>Reading requires {@code USER_READ}, because the role list is what an
 * admin screen needs to populate a role picker. Editing a role's permissions
 * requires {@code ROLE_MANAGE}, which only SUPER_ADMIN holds by default.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/roles")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Roles & permissions", description = "The RBAC catalogue and role-permission mapping")
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.USER_READ + "')")
    @Operation(summary = "List roles with their permissions")
    public List<RoleResponse> listRoles() {
        return roleService.listRoles();
    }

    @GetMapping("/{roleId}")
    @PreAuthorize("hasAuthority('" + Permissions.USER_READ + "')")
    @Operation(summary = "Get one role")
    public RoleResponse getRole(@PathVariable Long roleId) {
        return roleService.getRole(roleId);
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('" + Permissions.USER_READ + "')")
    @Operation(summary = "List every permission",
            description = "The full catalogue, grouped by module. Fixed at migration time - "
                    + "a permission only means something if code checks for it.")
    public List<PermissionResponse> listPermissions() {
        return roleService.listPermissions();
    }

    @PutMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('" + Permissions.ROLE_MANAGE + "')")
    @Operation(summary = "Replace a role's permissions",
            description = "Full replacement; send an empty array to strip the role. SUPER_ADMIN "
                    + "cannot be edited, since it is the role that can repair the others. Users "
                    + "already signed in pick the change up within 15 minutes, when their access "
                    + "token is next refreshed.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Permissions replaced"),
            @ApiResponse(responseCode = "400",
                    description = "An unknown permission name, or an attempt to edit SUPER_ADMIN (VALIDATION_FAILED)"),
            @ApiResponse(responseCode = "403", description = "Missing ROLE_MANAGE (ACCESS_DENIED)"),
            @ApiResponse(responseCode = "404", description = "No such role (RESOURCE_NOT_FOUND)")
    })
    public RoleResponse updatePermissions(
            @PathVariable Long roleId,
            @Valid @RequestBody UserAdminRequests.UpdateRolePermissions request,
            @AuthenticationPrincipal AuthenticatedUser caller,
            HttpServletRequest httpRequest) {
        return roleService.updatePermissions(roleId, request, caller, RequestContext.from(httpRequest));
    }
}
