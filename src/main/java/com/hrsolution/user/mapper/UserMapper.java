package com.hrsolution.user.mapper;

import com.hrsolution.user.dto.PermissionResponse;
import com.hrsolution.user.dto.RoleResponse;
import com.hrsolution.user.dto.UserResponse;
import com.hrsolution.user.entity.Permission;
import com.hrsolution.user.entity.Role;
import com.hrsolution.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Set;
import java.util.TreeSet;

/**
 * Entity to DTO mapping for users, roles and permissions.
 *
 * <p>The derived fields use {@code expression} because they come from behaviour
 * on the entity ({@code user.fullName()}, {@code user.isLocked()}) rather than
 * from a JavaBean property MapStruct could match by name.
 *
 * <p>Note what is never mapped: {@code passwordHash} and {@code tokenVersion}
 * have no counterpart on any response record, so the build's
 * {@code unmappedTargetPolicy=ERROR} cannot accidentally start exposing them -
 * adding them to a DTO would be a deliberate act, not an oversight.
 */
@Mapper
public interface UserMapper {

    @Mapping(target = "fullName", expression = "java(user.fullName())")
    @Mapping(target = "locked", expression = "java(user.isLocked())")
    @Mapping(target = "lockRemainingSeconds", expression = "java(user.lockRemainingSeconds())")
    @Mapping(target = "roles", expression = "java(user.roleNames())")
    UserResponse toUserResponse(User user);

    @Mapping(target = "permissions", expression = "java(permissionNames(role))")
    RoleResponse toRoleResponse(Role role);

    PermissionResponse toPermissionResponse(Permission permission);

    /** Sorted so the admin screen and API responses have a stable order. */
    default Set<String> permissionNames(Role role) {
        Set<String> names = new TreeSet<>();
        role.getPermissions().forEach(permission -> names.add(permission.getName()));
        return names;
    }
}
