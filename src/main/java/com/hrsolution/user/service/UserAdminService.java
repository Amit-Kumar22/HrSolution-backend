package com.hrsolution.user.service;

import com.hrsolution.audit.entity.AuditAction;
import com.hrsolution.audit.service.AuditService;
import com.hrsolution.auth.entity.RevokedReason;
import com.hrsolution.auth.service.RefreshTokenService;
import com.hrsolution.common.error.BusinessRuleException;
import com.hrsolution.common.error.DuplicateResourceException;
import com.hrsolution.common.error.FieldValidationException;
import com.hrsolution.common.error.ResourceNotFoundException;
import com.hrsolution.common.repository.SpecificationUtils;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.notification.service.EmailService;
import com.hrsolution.user.dto.UserAdminRequests;
import com.hrsolution.user.dto.UserResponse;
import com.hrsolution.user.entity.Role;
import com.hrsolution.user.entity.RoleName;
import com.hrsolution.user.entity.User;
import com.hrsolution.user.entity.UserStatus;
import com.hrsolution.user.mapper.UserMapper;
import com.hrsolution.user.repository.RoleRepository;
import com.hrsolution.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Administration of user accounts: listing, creating staff, assigning roles,
 * enabling and disabling, and approving client registrations.
 *
 * <p>Several operations here bump {@code tokenVersion}, which invalidates every
 * access token already issued to that user. Changing someone's roles or
 * disabling their account has to take effect now, not whenever their current
 * 15-minute token happens to expire.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final com.hrsolution.client.repository.ClientUserRepository clientUserRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final EmailService emailService;

    // ==================================================================
    // Queries
    // ==================================================================

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String search, UserStatus status, String role,
                                           Pageable pageable) {
        Specification<User> specification = Specification.<User>unrestricted()
                .and(SpecificationUtils.notDeleted())
                .and(SpecificationUtils.equal("status", status))
                .and(SpecificationUtils.equal("roles.name", role))
                .and(SpecificationUtils.anyContainsIgnoreCase(
                        search, "email", "firstName", "lastName", "pendingCompanyName"));

        Page<User> page = userRepository.findAll(specification, pageable);
        return PageResponse.from(page, userMapper::toUserResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long userId) {
        return userMapper.toUserResponse(loadUser(userId));
    }

    // ==================================================================
    // Create and edit
    // ==================================================================

    /**
     * Creates a staff account.
     *
     * <p>Created {@code ACTIVE} and already email-verified: an administrator
     * creating the account <em>is</em> the verification, and making a new
     * colleague wait for a link they may never receive serves nothing.
     */
    @Transactional
    public UserResponse create(UserAdminRequests.CreateUser request, AuthenticatedUser actor,
                               RequestContext context) {
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoringSoftDelete(email)) {
            throw DuplicateResourceException.of("An account", "email", email);
        }

        Set<Role> roles = resolveRoles(request.roles(), actor);

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(trimToNull(request.lastName()));
        user.setPhone(trimToNull(request.phone()));
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(Instant.now());
        user.setRoles(roles);

        User saved = userRepository.save(user);

        auditService.recordSecurityEvent(AuditAction.USER_CREATED, actor.id(), actor.email(), true,
                "Created user %s with roles %s".formatted(email, request.roles()), context);

        return userMapper.toUserResponse(saved);
    }

    @Transactional
    public UserResponse update(Long userId, UserAdminRequests.UpdateUser request,
                               AuthenticatedUser actor, RequestContext context) {
        User user = loadUser(userId);

        user.setFirstName(request.firstName().trim());
        user.setLastName(trimToNull(request.lastName()));
        user.setPhone(trimToNull(request.phone()));

        auditService.recordSecurityEvent(AuditAction.USER_UPDATED, actor.id(), actor.email(), true,
                "Updated profile of " + user.getEmail(), context);

        return userMapper.toUserResponse(user);
    }

    // ==================================================================
    // Roles
    // ==================================================================

    /**
     * Replaces the user's roles.
     *
     * <p>Bumps {@code tokenVersion} and revokes refresh tokens. Without that, a
     * user demoted from ACCOUNTS would keep payroll access for the life of
     * their current access token - and could refresh it to keep going.
     */
    @Transactional
    public UserResponse assignRoles(Long userId, UserAdminRequests.AssignRoles request,
                                    AuthenticatedUser actor, RequestContext context) {
        User user = loadUser(userId);
        Set<String> previousRoles = user.roleNames();

        guardAgainstSelfPrivilegeChange(user, actor, "roles");

        Set<Role> roles = resolveRoles(request.roles(), actor);
        user.setRoles(roles);
        user.incrementTokenVersion();
        refreshTokenService.revokeAllForUser(user.getId(), RevokedReason.ADMIN_REVOKED);

        log.info("Roles for {} changed from {} to {} by {}",
                user.getEmail(), previousRoles, request.roles(), actor.email());

        auditService.recordEntityChange(AuditAction.ROLE_ASSIGNED, "User", user.getId(),
                actor.id(), actor.email(),
                "Roles changed for " + user.getEmail(),
                previousRoles.toString(), request.roles().toString(), context);

        return userMapper.toUserResponse(user);
    }

    // ==================================================================
    // Enable / disable
    // ==================================================================

    @Transactional
    public UserResponse disable(Long userId, UserAdminRequests.DisableUser request,
                                AuthenticatedUser actor, RequestContext context) {
        User user = loadUser(userId);
        guardAgainstSelfPrivilegeChange(user, actor, "status");

        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BusinessRuleException("This account is already disabled.");
        }

        user.setStatus(UserStatus.DISABLED);
        // Both are needed: the version kills live access tokens, the revoke
        // stops the user minting new ones from a refresh cookie.
        user.incrementTokenVersion();
        refreshTokenService.revokeAllForUser(user.getId(), RevokedReason.ADMIN_REVOKED);

        auditService.recordSecurityEvent(AuditAction.USER_DISABLED, actor.id(), actor.email(), true,
                "Disabled %s%s".formatted(user.getEmail(),
                        request.reason() == null ? "" : " - " + request.reason()), context);

        return userMapper.toUserResponse(user);
    }

    @Transactional
    public UserResponse enable(Long userId, AuthenticatedUser actor, RequestContext context) {
        User user = loadUser(userId);

        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new BusinessRuleException("This account is already active.");
        }

        user.setStatus(UserStatus.ACTIVE);
        // Re-enabling does not resurrect old sessions; the user signs in again.
        user.setFailedAttempts(0);
        user.setLockedUntil(null);

        auditService.recordSecurityEvent(AuditAction.USER_ENABLED, actor.id(), actor.email(), true,
                "Enabled " + user.getEmail(), context);

        return userMapper.toUserResponse(user);
    }

    /** Clears a failed-login lockout without waiting for it to expire. */
    @Transactional
    public UserResponse unlock(Long userId, AuthenticatedUser actor, RequestContext context) {
        User user = loadUser(userId);

        user.setFailedAttempts(0);
        user.setLockedUntil(null);

        auditService.recordSecurityEvent(AuditAction.USER_UPDATED, actor.id(), actor.email(), true,
                "Cleared the sign-in lockout on " + user.getEmail(), context);

        return userMapper.toUserResponse(user);
    }

    // ==================================================================
    // Client registration approval
    // ==================================================================

    /**
     * Activates a client login without creating a client company record.
     *
     * <p><strong>Prefer {@code POST /clients/approve-registration/{userId}}.</strong>
     * Since Phase 4 that endpoint does the whole job in one transaction: it
     * creates the {@code clients} row, links this login to it as primary
     * contact, and activates the account.
     *
     * <p>This method activates only the login, which leaves the user able to
     * sign in and see nothing - every client-scoped screen resolves through
     * {@code client_users}, and there is no row there yet. It is kept for the
     * rare case of re-activating a login whose client already exists, and
     * refuses to run when no such link is present rather than producing that
     * broken half-state.
     */
    @Transactional
    public UserResponse approveClient(Long userId, AuthenticatedUser actor,
                                      RequestContext context) {
        User user = loadUser(userId);

        if (!user.hasRole(RoleName.CLIENT)) {
            throw new BusinessRuleException("This account is not a client registration.");
        }
        if (user.getStatus() != UserStatus.PENDING_APPROVAL) {
            throw new BusinessRuleException(
                    "Only a PENDING_APPROVAL registration can be approved; this one is "
                            + user.getStatus() + ".");
        }
        // The guard that stops this creating a sign-in-but-see-nothing account.
        if (!clientUserRepository.existsByUserId(userId)) {
            throw new BusinessRuleException(
                    "This login is not linked to a client company, so activating it alone would "
                            + "let the user sign in and see nothing. Use "
                            + "POST /api/v1/clients/approve-registration/" + userId
                            + " instead - it creates the client record and links this login in "
                            + "one step.");
        }

        user.setStatus(UserStatus.ACTIVE);
        // An admin approving the registration vouches for the address.
        user.markEmailVerified();

        auditService.recordSecurityEvent(AuditAction.CLIENT_APPROVED, actor.id(), actor.email(),
                true, "Activated client login %s (client record already linked)"
                        .formatted(user.getEmail()), context);

        return userMapper.toUserResponse(user);
    }

    @Transactional
    public UserResponse rejectClient(Long userId, String reason, AuthenticatedUser actor,
                                     RequestContext context) {
        User user = loadUser(userId);

        if (user.getStatus() != UserStatus.PENDING_APPROVAL) {
            throw new BusinessRuleException(
                    "Only a PENDING_APPROVAL registration can be rejected; this one is "
                            + user.getStatus() + ".");
        }

        user.setStatus(UserStatus.REJECTED);

        auditService.recordSecurityEvent(AuditAction.CLIENT_REJECTED, actor.id(), actor.email(),
                true, "Rejected client registration for '%s'%s"
                        .formatted(user.getPendingCompanyName(),
                                reason == null ? "" : " - " + reason), context);

        return userMapper.toUserResponse(user);
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private User loadUser(Long userId) {
        return userRepository.findActiveByIdWithRoles(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    }

    /**
     * Resolves role names to entities, rejecting unknown ones.
     *
     * <p>Only a SUPER_ADMIN may grant SUPER_ADMIN. Otherwise an ADMIN - who by
     * design does not hold {@code SETTINGS_MANAGE} - could grant themselves,
     * or a colleague, a role that does, and walk around the restriction.
     */
    private Set<Role> resolveRoles(Set<String> roleNames, AuthenticatedUser actor) {
        Set<Role> resolved = new HashSet<>();

        for (String roleName : roleNames) {
            String normalised = roleName == null ? "" : roleName.trim().toUpperCase();

            if (RoleName.SUPER_ADMIN.name().equals(normalised) && !actor.isSuperAdmin()) {
                throw FieldValidationException.of("roles",
                        "only a SUPER_ADMIN can grant the SUPER_ADMIN role");
            }

            Role role = roleRepository.findWithPermissionsByName(normalised)
                    .orElseThrow(() -> FieldValidationException.of("roles",
                            "'%s' is not a known role".formatted(roleName)));
            resolved.add(role);
        }
        return resolved;
    }

    /**
     * Stops an administrator changing their own roles or disabling themselves.
     *
     * <p>Guards against two different accidents: locking the last SUPER_ADMIN
     * out of the system, and an admin quietly escalating their own privileges
     * with no second person involved.
     */
    private void guardAgainstSelfPrivilegeChange(User target, AuthenticatedUser actor, String what) {
        if (target.getId().equals(actor.id())) {
            throw new BusinessRuleException(
                    "You cannot change your own %s. Ask another administrator.".formatted(what));
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
