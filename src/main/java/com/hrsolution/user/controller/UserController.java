package com.hrsolution.user.controller;

import com.hrsolution.auth.dto.MessageResponse;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.security.Permissions;
import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.user.dto.UserAdminRequests;
import com.hrsolution.user.dto.UserResponse;
import com.hrsolution.user.entity.UserStatus;
import com.hrsolution.user.service.UserAdminService;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

/**
 * User administration.
 *
 * <p>Every method is guarded by a permission, not a role name - so the
 * role-to-permission mapping stays editable from
 * {@link RoleController} without touching this class.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Users", description = "User accounts, role assignment and client approval")
public class UserController {

    private final UserAdminService userAdminService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.USER_READ + "')")
    @Operation(summary = "List users",
            description = "Paginated and filterable. Omitted filters are ignored.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A page of users"),
            @ApiResponse(responseCode = "403", description = "Missing USER_READ (ACCESS_DENIED)")
    })
    public PageResponse<UserResponse> list(
            @Parameter(description = "Free text across email, name and pending company name")
            @RequestParam(required = false) String search,
            @Parameter(description = "Exact status match")
            @RequestParam(required = false) UserStatus status,
            @Parameter(description = "Exact role name, e.g. ACCOUNTS")
            @RequestParam(required = false) String role,
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return userAdminService.list(search, status, role, pageable);
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('" + Permissions.USER_READ + "')")
    @Operation(summary = "Get one user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The user"),
            @ApiResponse(responseCode = "404", description = "No such user (RESOURCE_NOT_FOUND)")
    })
    public UserResponse get(@PathVariable Long userId) {
        return userAdminService.get(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.USER_MANAGE + "')")
    @Operation(summary = "Create a staff account",
            description = "Created ACTIVE and pre-verified, since an administrator creating the "
                    + "account is itself the verification. Granting SUPER_ADMIN requires SUPER_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "400", description = "Validation failed, or an unknown role (VALIDATION_FAILED)"),
            @ApiResponse(responseCode = "409", description = "Email already registered (DUPLICATE_RESOURCE)")
    })
    public UserResponse create(@Valid @RequestBody UserAdminRequests.CreateUser request,
                               @AuthenticationPrincipal AuthenticatedUser caller,
                               HttpServletRequest httpRequest) {
        return userAdminService.create(request, caller, RequestContext.from(httpRequest));
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasAuthority('" + Permissions.USER_MANAGE + "')")
    @Operation(summary = "Update a user's profile",
            description = "Name and phone only. Email is immutable, and roles and status have "
                    + "their own endpoints so each change is audited distinctly.")
    public UserResponse update(@PathVariable Long userId,
                               @Valid @RequestBody UserAdminRequests.UpdateUser request,
                               @AuthenticationPrincipal AuthenticatedUser caller,
                               HttpServletRequest httpRequest) {
        return userAdminService.update(userId, request, caller, RequestContext.from(httpRequest));
    }

    @PutMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('" + Permissions.ROLE_MANAGE + "')")
    @Operation(summary = "Replace a user's roles",
            description = "Full replacement. Signs the user out everywhere and invalidates their "
                    + "live access tokens, so the change takes effect at once. You cannot change "
                    + "your own roles.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Roles replaced; the user's sessions were revoked"),
            @ApiResponse(responseCode = "400", description = "Unknown role, or SUPER_ADMIN granted by a non-SUPER_ADMIN"),
            @ApiResponse(responseCode = "422", description = "Attempted to change your own roles (BUSINESS_RULE_VIOLATION)")
    })
    public UserResponse assignRoles(@PathVariable Long userId,
                                    @Valid @RequestBody UserAdminRequests.AssignRoles request,
                                    @AuthenticationPrincipal AuthenticatedUser caller,
                                    HttpServletRequest httpRequest) {
        return userAdminService.assignRoles(userId, request, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/{userId}/disable")
    @PreAuthorize("hasAuthority('" + Permissions.USER_MANAGE + "')")
    @Operation(summary = "Disable an account",
            description = "Revokes every session and invalidates live access tokens immediately. "
                    + "The row is kept - accounts are never hard-deleted.")
    public UserResponse disable(@PathVariable Long userId,
                                @Valid @RequestBody UserAdminRequests.DisableUser request,
                                @AuthenticationPrincipal AuthenticatedUser caller,
                                HttpServletRequest httpRequest) {
        return userAdminService.disable(userId, request, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/{userId}/enable")
    @PreAuthorize("hasAuthority('" + Permissions.USER_MANAGE + "')")
    @Operation(summary = "Re-enable an account",
            description = "Sets the account ACTIVE and clears any lockout. Old sessions are not "
                    + "restored; the user signs in again.")
    public UserResponse enable(@PathVariable Long userId,
                               @AuthenticationPrincipal AuthenticatedUser caller,
                               HttpServletRequest httpRequest) {
        return userAdminService.enable(userId, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/{userId}/unlock")
    @PreAuthorize("hasAuthority('" + Permissions.USER_MANAGE + "')")
    @Operation(summary = "Clear a sign-in lockout",
            description = "Resets the failed-attempt counter so the user need not wait out the "
                    + "15-minute lock.")
    public UserResponse unlock(@PathVariable Long userId,
                               @AuthenticationPrincipal AuthenticatedUser caller,
                               HttpServletRequest httpRequest) {
        return userAdminService.unlock(userId, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/{userId}/approve-client")
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_APPROVE + "')")
    @Operation(summary = "Approve a client registration",
            description = "Activates a PENDING_APPROVAL client login. Phase 4 extends this to also "
                    + "create the client company record from the self-declared company name.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Approved; the client can now sign in"),
            @ApiResponse(responseCode = "422",
                    description = "Not a client registration, or not PENDING_APPROVAL (BUSINESS_RULE_VIOLATION)")
    })
    public UserResponse approveClient(@PathVariable Long userId,
                                      @AuthenticationPrincipal AuthenticatedUser caller,
                                      HttpServletRequest httpRequest) {
        return userAdminService.approveClient(userId, caller, RequestContext.from(httpRequest));
    }

    @PatchMapping("/{userId}/reject-client")
    @PreAuthorize("hasAuthority('" + Permissions.CLIENT_APPROVE + "')")
    @Operation(summary = "Reject a client registration")
    public UserResponse rejectClient(@PathVariable Long userId,
                                     @RequestParam(required = false) String reason,
                                     @AuthenticationPrincipal AuthenticatedUser caller,
                                     HttpServletRequest httpRequest) {
        return userAdminService.rejectClient(userId, reason, caller, RequestContext.from(httpRequest));
    }

    @GetMapping("/permissions-check")
    @Operation(summary = "Smoke-test your own authorities",
            description = "Returns the permissions the server currently derives for you from the "
                    + "database. Handy for confirming a role change took effect.")
    public MessageResponse permissionsCheck(@AuthenticationPrincipal AuthenticatedUser caller) {
        return MessageResponse.of("%s holds %d permission(s): %s"
                .formatted(caller.email(), caller.permissions().size(), caller.permissions()));
    }
}
