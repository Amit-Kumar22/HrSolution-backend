package com.hrsolution.common.security;

import com.hrsolution.common.error.ErrorCode;
import com.hrsolution.user.entity.RoleName;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Static access to the authenticated caller, for service-layer ownership checks.
 *
 * <p>Controllers should prefer {@code @AuthenticationPrincipal AuthenticatedUser}
 * - it is explicit and trivially testable. This class exists for services,
 * which must be able to answer "whose data is this?" without every method
 * signature growing a caller parameter.
 *
 * <p><strong>Permission checks do not replace ownership checks.</strong> A
 * {@code CLIENT} holding {@code INVOICE_READ} may read invoices - but only its
 * own. The permission is checked by {@code @PreAuthorize} on the controller;
 * the ownership restriction belongs in the service, using these helpers.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<AuthenticatedUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /**
     * The caller, or a 401 if there is none.
     *
     * <p>Use on paths that are already behind authentication, where an absent
     * principal means a filter chain misconfiguration rather than an anonymous
     * visitor.
     */
    public static AuthenticatedUser requireCurrentUser() {
        return currentUser().orElseThrow(() -> new UnauthenticatedException(
                "No authenticated user is bound to this request."));
    }

    public static Optional<Long> currentUserId() {
        return currentUser().map(AuthenticatedUser::id);
    }

    public static Optional<String> currentUserEmail() {
        return currentUser().map(AuthenticatedUser::email);
    }

    public static boolean hasPermission(String permission) {
        return currentUser().map(user -> user.hasPermission(permission)).orElse(false);
    }

    public static boolean hasRole(RoleName roleName) {
        return currentUser().map(user -> user.hasRole(roleName)).orElse(false);
    }

    /**
     * Whether the caller is the owner of {@code ownerUserId}, or holds an
     * override permission such as {@code WORKER_READ} that legitimately allows
     * seeing other people's records.
     */
    public static boolean isOwnerOr(Long ownerUserId, String overridePermission) {
        return currentUser()
                .map(user -> user.id().equals(ownerUserId) || user.hasPermission(overridePermission))
                .orElse(false);
    }

    /** Thrown when a principal is required but absent. Maps to HTTP 401. */
    public static class UnauthenticatedException extends com.hrsolution.common.error.ApiException {

        private static final long serialVersionUID = 1L;

        public UnauthenticatedException(String message) {
            super(ErrorCode.UNAUTHENTICATED, org.springframework.http.HttpStatus.UNAUTHORIZED, message);
        }
    }
}
