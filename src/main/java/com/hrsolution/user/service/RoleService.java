package com.hrsolution.user.service;

import com.hrsolution.audit.entity.AuditAction;
import com.hrsolution.audit.service.AuditService;
import com.hrsolution.common.error.FieldValidationException;
import com.hrsolution.common.error.ResourceNotFoundException;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.user.dto.PermissionResponse;
import com.hrsolution.user.dto.RoleResponse;
import com.hrsolution.user.dto.UserAdminRequests;
import com.hrsolution.user.entity.Permission;
import com.hrsolution.user.entity.Role;
import com.hrsolution.user.entity.RoleName;
import com.hrsolution.user.mapper.UserMapper;
import com.hrsolution.user.repository.PermissionRepository;
import com.hrsolution.user.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the role catalogue and edits role-to-permission mappings.
 *
 * <p>Roles cannot be created or deleted through the API. The nine seeded roles
 * are referenced by name in application logic, and a tenth role would grant
 * nothing that an existing one cannot - what is genuinely useful, and what this
 * service allows, is re-pointing which permissions a role carries.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserMapper userMapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return roleRepository.findAllByOrderByNameAsc().stream()
                .map(userMapper::toRoleResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions() {
        return permissionRepository.findAllByOrderByModuleAscNameAsc().stream()
                .map(userMapper::toPermissionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse getRole(Long roleId) {
        return userMapper.toRoleResponse(loadRole(roleId));
    }

    /**
     * Replaces the permissions a role grants.
     *
     * <p>SUPER_ADMIN is excluded. It is the role that can repair every other
     * role's permissions, so letting it strip its own would be a one-click way
     * to make the system unadministrable with no path back short of editing the
     * database by hand.
     *
     * <p>Users already signed in keep their current authorities until their
     * access token expires - at most 15 minutes - because authorities are
     * rebuilt per request from the user's roles, and this changes the role
     * rather than the user. Revoking every affected user's sessions was
     * considered and rejected: a permission tweak should not sign out the whole
     * company.
     */
    @Transactional
    public RoleResponse updatePermissions(Long roleId,
                                          UserAdminRequests.UpdateRolePermissions request,
                                          AuthenticatedUser actor,
                                          RequestContext context) {
        Role role = loadRole(roleId);

        if (RoleName.SUPER_ADMIN.name().equals(role.getName())) {
            throw FieldValidationException.of("permissions",
                    "the SUPER_ADMIN role always holds every permission and cannot be edited");
        }

        Set<String> previous = userMapper.permissionNames(role);
        Set<Permission> replacements = resolvePermissions(request.permissions());

        role.replacePermissions(replacements);

        log.info("Permissions for role {} changed from {} to {} by {}",
                role.getName(), previous, request.permissions(), actor.email());

        auditService.recordEntityChange(AuditAction.ROLE_PERMISSIONS_CHANGED, "Role", role.getId(),
                actor.id(), actor.email(),
                "Permissions changed for role " + role.getName(),
                previous.toString(), request.permissions().toString(), context);

        return userMapper.toRoleResponse(role);
    }

    private Set<Permission> resolvePermissions(Set<String> permissionNames) {
        if (permissionNames == null || permissionNames.isEmpty()) {
            return new HashSet<>();
        }

        Set<String> normalised = new HashSet<>();
        permissionNames.forEach(name -> normalised.add(name == null ? "" : name.trim().toUpperCase()));

        Set<Permission> found = permissionRepository.findByNameIn(normalised);

        // Report the unknown names rather than silently dropping them - a typo
        // that quietly grants nothing is far harder to notice than an error.
        if (found.size() != normalised.size()) {
            Set<String> knownNames = new HashSet<>();
            found.forEach(permission -> knownNames.add(permission.getName()));
            normalised.removeAll(knownNames);
            throw FieldValidationException.of("permissions",
                    "unknown permission(s): " + normalised);
        }
        return found;
    }

    private Role loadRole(Long roleId) {
        return roleRepository.findWithPermissionsById(roleId)
                .orElseThrow(() -> ResourceNotFoundException.of("Role", roleId));
    }
}
