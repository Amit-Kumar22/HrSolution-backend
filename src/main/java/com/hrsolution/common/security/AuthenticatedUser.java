package com.hrsolution.common.security;

import com.hrsolution.user.entity.RoleName;
import com.hrsolution.user.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The authenticated caller, as exposed to controllers and services.
 *
 * <pre>{@code
 * @GetMapping("/me/payslips")
 * public List<PayslipResponse> mine(@AuthenticationPrincipal AuthenticatedUser caller) {
 *     return payslipService.forWorker(caller.id());
 * }
 * }</pre>
 *
 * <p>Implements {@link Principal} so that {@code Authentication.getName()}
 * yields the email - which is what {@code AuditorAwareImpl} writes into the
 * {@code created_by}/{@code updated_by} columns.
 *
 * @param id          the user id, from the token's {@code sub} claim
 * @param email       login identifier
 * @param fullName    for display and emails
 * @param roles       role names, e.g. {@code ADMIN}
 * @param permissions the union of permissions across those roles
 */
public record AuthenticatedUser(
        Long id,
        String email,
        String fullName,
        Set<String> roles,
        Set<String> permissions) implements Principal {

    /** Prefix Spring Security expects for {@code hasRole(...)} checks. */
    public static final String ROLE_PREFIX = "ROLE_";

    /**
     * Built from the freshly loaded entity, never from token claims - so a role
     * revoked a minute ago takes effect on the next request rather than
     * whenever the access token happens to expire.
     */
    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(
                user.getId(),
                user.getEmail(),
                user.fullName(),
                user.roleNames(),
                user.permissionNames());
    }

    @Override
    public String getName() {
        return email;
    }

    /**
     * Permissions become authorities verbatim, so
     * {@code hasAuthority('PAYROLL_PROCESS')} works; roles are additionally
     * exposed with the {@code ROLE_} prefix so {@code hasRole('ADMIN')} works
     * for the few checks that are genuinely about the kind of account.
     */
    public Collection<GrantedAuthority> authorities() {
        List<GrantedAuthority> authorities = new ArrayList<>(permissions.size() + roles.size());
        permissions.forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        roles.forEach(role -> authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + role)));
        return authorities;
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    public boolean hasRole(RoleName roleName) {
        return roles.contains(roleName.name());
    }

    public boolean isSuperAdmin() {
        return hasRole(RoleName.SUPER_ADMIN);
    }
}
