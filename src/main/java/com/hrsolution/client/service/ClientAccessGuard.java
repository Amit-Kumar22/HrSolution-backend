package com.hrsolution.client.service;

import com.hrsolution.client.entity.ClientUser;
import com.hrsolution.client.repository.ClientUserRepository;
import com.hrsolution.common.error.ApiException;
import com.hrsolution.common.error.ErrorCode;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.user.entity.RoleName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Enforces that a client user can only reach their own company's data.
 *
 * <h2>Why a permission is not enough</h2>
 *
 * <p>A {@code CLIENT}-role user holds {@code INVOICE_READ}, so
 * {@code @PreAuthorize("hasAuthority('INVOICE_READ')")} lets them call the
 * invoice endpoints — as it should. What the annotation cannot express is
 * <em>whose</em> invoices. That is this class's job, and it is the difference
 * between a working portal and a data breach: client ids are sequential
 * integers, so without an ownership check any client user could read a
 * competitor's rates, workers and invoices by changing a number in the URL.
 *
 * <p>The rule throughout: <strong>a client id supplied by the caller is never
 * trusted.</strong> It is either checked against the caller's own
 * ({@link #requireAccessTo}) or ignored entirely in favour of the resolved one
 * ({@link #resolveAccessibleClientId}).
 *
 * <h2>Staff pass through</h2>
 *
 * <p>Internal staff — anyone whose role is not CLIENT — are unrestricted here;
 * their limits come from permissions. The check applies specifically to
 * external accounts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientAccessGuard {

    private final ClientUserRepository clientUserRepository;

    /**
     * Raised when a client user reaches for another client's data.
     *
     * <p>Deliberately 404, not 403. A 403 confirms the record exists, which
     * turns sequential ids into a way to enumerate the customer list. "Not
     * found" is also the honest answer from that caller's point of view.
     */
    public static class ClientAccessDeniedException extends ApiException {

        private static final long serialVersionUID = 1L;

        public ClientAccessDeniedException(String message) {
            super(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, message);
        }
    }

    /** True when the caller is an external client account rather than staff. */
    public boolean isClientUser(AuthenticatedUser caller) {
        return caller != null && caller.hasRole(RoleName.CLIENT);
    }

    /**
     * The client this user belongs to, or empty for staff.
     *
     * <p>Resolved from {@code client_users}, never from the request.
     */
    @Transactional(readOnly = true)
    public Optional<Long> ownClientId(AuthenticatedUser caller) {
        if (!isClientUser(caller)) {
            return Optional.empty();
        }
        return clientUserRepository.findActiveByUserId(caller.id())
                .map(link -> link.getClient().getId());
    }

    /**
     * Checks that the caller may act on {@code clientId}.
     *
     * <p>Staff pass. A client user passes only for their own client. Call this
     * at the top of every service method that takes a client id from the
     * caller.
     *
     * @throws ClientAccessDeniedException if the caller is a client user asking
     *                                     about a different client, or an
     *                                     unlinked one
     */
    @Transactional(readOnly = true)
    public void requireAccessTo(Long clientId, AuthenticatedUser caller) {
        if (!isClientUser(caller)) {
            return;
        }

        Long ownClientId = clientUserRepository.findActiveByUserId(caller.id())
                .map(link -> link.getClient().getId())
                .orElseThrow(() -> {
                    // A CLIENT login with no client link is a broken approval,
                    // not an attack. Logged as a warning because it means
                    // somebody cannot work.
                    log.warn("User {} holds the CLIENT role but is not linked to any client",
                            caller.email());
                    return new ClientAccessDeniedException(
                            "Your account is not linked to a client company. Please contact support.");
                });

        if (!ownClientId.equals(clientId)) {
            // Logged at WARN with both ids: a client user probing other ids is
            // worth seeing, and it is indistinguishable from a UI bug without
            // the log.
            log.warn("Client user {} (client {}) attempted to access client {}",
                    caller.email(), ownClientId, clientId);
            throw new ClientAccessDeniedException("No such client.");
        }
    }

    /**
     * Resolves which client a listing should be scoped to.
     *
     * <p>For a client user this returns their own id <em>whatever</em>
     * {@code requestedClientId} says, so a forged query parameter cannot widen
     * the result set. For staff it returns the requested filter unchanged,
     * including null to mean "all clients".
     */
    @Transactional(readOnly = true)
    public Long resolveAccessibleClientId(Long requestedClientId, AuthenticatedUser caller) {
        if (!isClientUser(caller)) {
            return requestedClientId;
        }

        Long ownClientId = clientUserRepository.findActiveByUserId(caller.id())
                .map(link -> link.getClient().getId())
                .orElseThrow(() -> new ClientAccessDeniedException(
                        "Your account is not linked to a client company. Please contact support."));

        if (requestedClientId != null && !requestedClientId.equals(ownClientId)) {
            log.warn("Client user {} (client {}) filtered a list by client {}; overriding",
                    caller.email(), ownClientId, requestedClientId);
        }
        return ownClientId;
    }

    /** The full link row, for screens that need the designation or contact flag. */
    @Transactional(readOnly = true)
    public Optional<ClientUser> ownClientLink(AuthenticatedUser caller) {
        if (!isClientUser(caller)) {
            return Optional.empty();
        }
        return clientUserRepository.findActiveByUserId(caller.id());
    }
}
