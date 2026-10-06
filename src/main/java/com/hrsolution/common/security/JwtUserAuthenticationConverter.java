package com.hrsolution.common.security;

import com.hrsolution.user.entity.User;
import com.hrsolution.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a verified access token into an {@link AuthenticatedUser} principal.
 *
 * <p>By the time this runs, the signature, issuer and expiry have already been
 * checked by the {@code JwtDecoder}. What remains are the questions a signature
 * cannot answer, because the token was minted minutes ago and says nothing
 * about what has happened since:
 *
 * <ul>
 *   <li>Does the account still exist, and is it still enabled?</li>
 *   <li>Does the token's {@code tokenVersion} still match the user's? A
 *       mismatch means the password changed, roles changed, or an administrator
 *       disabled the account - all of which must invalidate tokens issued
 *       earlier.</li>
 * </ul>
 *
 * <p><strong>This costs one primary-key lookup per authenticated request.</strong>
 * That is a deliberate trade against pure statelessness. Without it, revocation
 * could not take effect until the access token expired, so a disabled account
 * would keep working for up to 15 minutes - unacceptable when the thing being
 * revoked may be a departed employee's access to payroll. The query is a
 * single indexed read with roles and permissions fetched in the same statement.
 *
 * <p>Authorities are rebuilt from the database rather than read from the
 * token's {@code roles}/{@code permissions} claims. Those claims are useful to
 * clients deciding what to display, but trusting them for authorisation would
 * mean a permission removed from a role stays in force until every outstanding
 * token expires.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUserAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_PERMISSIONS = "permissions";
    public static final String CLAIM_TOKEN_VERSION = "tokenVersion";

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long userId = parseSubject(jwt);

        User user = userRepository.findActiveByIdWithRoles(userId)
                // Generic message on purpose: the client only needs to know the
                // token is unusable, not whether the account was deleted.
                .orElseThrow(() -> {
                    log.warn("Access token presented for unknown or deleted user id {}", userId);
                    return new InvalidBearerTokenException("The access token is no longer valid.");
                });

        if (!user.getStatus().canLogin()) {
            log.warn("Access token presented for user {} in status {}", user.getEmail(), user.getStatus());
            throw new InvalidBearerTokenException("The access token is no longer valid.");
        }

        // Read as Number, not Integer. A JWT claim written as an int comes back
        // from JSON as a Long, so casting straight to Integer throws
        // ClassCastException on every single authenticated request.
        Object rawTokenVersion = jwt.getClaim(CLAIM_TOKEN_VERSION);
        if (!(rawTokenVersion instanceof Number tokenVersion)
                || tokenVersion.intValue() != user.getTokenVersion()) {
            log.warn("Access token for user {} carries tokenVersion {} but the account is at {}; rejecting",
                    user.getEmail(), rawTokenVersion, user.getTokenVersion());
            throw new InvalidBearerTokenException("The access token has been invalidated. Sign in again.");
        }

        AuthenticatedUser principal = AuthenticatedUser.from(user);
        return new UsernamePasswordAuthenticationToken(principal, jwt, principal.authorities());
    }

    private Long parseSubject(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new InvalidBearerTokenException("The access token has no subject claim.");
        }
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException e) {
            // Signed, so this is not tampering - more likely a token minted by
            // an older version of this application with a different subject
            // format.
            throw new InvalidBearerTokenException("The access token subject is not a user id.");
        }
    }
}
