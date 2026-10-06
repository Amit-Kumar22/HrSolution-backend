package com.hrsolution.auth.service;

import com.hrsolution.audit.entity.AuditAction;
import com.hrsolution.audit.service.AuditService;
import com.hrsolution.auth.entity.RefreshToken;
import com.hrsolution.auth.entity.RevokedReason;
import com.hrsolution.auth.repository.RefreshTokenRepository;
import com.hrsolution.common.config.AuthProperties;
import com.hrsolution.common.error.ApiException;
import com.hrsolution.common.error.ErrorCode;
import com.hrsolution.common.security.SecureTokens;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Issues, rotates and revokes refresh tokens.
 *
 * <h2>Rotation and reuse detection</h2>
 *
 * <p>Every call to {@code /auth/refresh} consumes the presented token and
 * issues a replacement. All tokens descended from one login share a
 * {@code familyId}, and each consumed row records the id of its successor,
 * giving a verifiable chain.
 *
 * <p>That chain is what makes theft detectable. Consider a token stolen from a
 * victim's browser:
 *
 * <ol>
 *   <li>The victim refreshes. Token A is consumed, token B is issued.</li>
 *   <li>The attacker replays token A - which is now revoked.</li>
 *   <li>A revoked token being presented has only one plausible explanation:
 *       two parties hold the same token, so it leaked.</li>
 *   <li>The <em>whole family</em> is revoked, including the attacker's and the
 *       victim's. Both are forced to log in again, which the attacker cannot do
 *       without the password.</li>
 * </ol>
 *
 * <p>Revoking the entire family, rather than only the replayed token, is the
 * point. If only token A were revoked, the attacker would still hold whatever
 * they obtained by using it, and the theft would continue undetected.
 *
 * <p>The cost is that a legitimate client which retries a refresh after a
 * network timeout can log itself out. That is the right trade for a system
 * holding payroll and identity documents, and it is why the access token's
 * 15-minute life keeps the refresh endpoint off the hot path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthProperties authProperties;
    private final AuditService auditService;
    private final RefreshTokenRevoker refreshTokenRevoker;

    /**
     * A newly issued refresh token.
     *
     * @param plaintext the value for the client's cookie. Returned exactly
     *                  once - only its hash is stored, so it cannot be
     *                  recovered afterwards.
     */
    public record IssuedRefreshToken(Long tokenId, String plaintext, Instant expiresAt, String familyId) {
    }

    /** Result of a rotation: the successor token, and who it belongs to. */
    public record RotationResult(Long userId, IssuedRefreshToken token) {
    }

    /** Raised for any unusable refresh token. Maps to HTTP 401. */
    public static class InvalidRefreshTokenException extends ApiException {

        private static final long serialVersionUID = 1L;

        public InvalidRefreshTokenException(String message) {
            super(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, message);
        }
    }

    // ------------------------------------------------------------------
    // Issuing
    // ------------------------------------------------------------------

    /** Starts a new family. Called on a successful login, once per device. */
    @Transactional
    public IssuedRefreshToken issueNewFamily(User user, boolean rememberMe, RequestContext context) {
        return issue(user, rememberMe, UUID.randomUUID().toString(), context);
    }

    private IssuedRefreshToken issue(User user, boolean rememberMe,
                                     String familyId, RequestContext context) {
        AuthProperties.Refresh settings = authProperties.getRefresh();
        Duration ttl = rememberMe ? settings.getRememberMeTtl() : settings.getTtl();

        String plaintext = SecureTokens.generate();
        Instant expiresAt = Instant.now().plus(ttl);

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(SecureTokens.hash(plaintext));
        token.setFamilyId(familyId);
        token.setExpiresAt(expiresAt);
        token.setRememberMe(rememberMe);
        token.setIpAddress(context.ipAddress());
        token.setUserAgent(context.userAgent());

        RefreshToken saved = refreshTokenRepository.save(token);
        return new IssuedRefreshToken(saved.getId(), plaintext, expiresAt, familyId);
    }

    // ------------------------------------------------------------------
    // Rotation
    // ------------------------------------------------------------------

    /**
     * Validates and consumes {@code presentedToken}, returning its successor.
     *
     * @throws InvalidRefreshTokenException if the token is unknown, expired, or
     *                                      already consumed (in which case the
     *                                      whole family is revoked first)
     */
    @Transactional
    public RotationResult rotate(String presentedToken, RequestContext context) {
        String presentedHash = SecureTokens.hash(presentedToken);

        RefreshToken existing = refreshTokenRepository.findByTokenHash(presentedHash)
                .orElseThrow(() -> {
                    // Either fabricated, or a token old enough to have been
                    // purged. Nothing to revoke either way.
                    log.warn("Refresh token presented that matches no stored hash, from ip={}",
                            context.ipAddress());
                    return new InvalidRefreshTokenException("Session expired. Please sign in again.");
                });

        User user = existing.getUser();

        // Checked BEFORE expiry: a replayed token is evidence of theft whether
        // or not it has since expired, and the family must be cut either way.
        if (existing.isRevoked()) {
            handleReuseDetected(existing, user, context);
            throw new InvalidRefreshTokenException(
                    "This session is no longer valid. Please sign in again.");
        }

        if (existing.isExpired()) {
            existing.revoke(RevokedReason.ROTATED);
            throw new InvalidRefreshTokenException("Session expired. Please sign in again.");
        }

        // The account may have been disabled since this token was issued.
        if (!user.getStatus().canLogin()) {
            // Must commit independently - this method throws next, and the
            // rollback would otherwise undo the revocation.
            refreshTokenRevoker.revokeAllForUserNow(user.getId(), RevokedReason.ADMIN_REVOKED);
            throw new InvalidRefreshTokenException("This account is not active.");
        }

        // Carry rememberMe across the rotation, so a "remember me" session is
        // not quietly downgraded to 7 days on its first refresh.
        IssuedRefreshToken successor =
                issue(user, existing.isRememberMe(), existing.getFamilyId(), context);

        existing.setReplacedByTokenId(successor.tokenId());
        existing.revoke(RevokedReason.ROTATED);

        auditService.recordSecurityEvent(AuditAction.TOKEN_REFRESHED, user.getId(), user.getEmail(),
                true, "Refresh token rotated", context);

        return new RotationResult(user.getId(), successor);
    }

    /**
     * Revokes every live token in the family and records a security event.
     *
     * <p>This is the loudest thing this service does, and intentionally so:
     * reaching it means a token was held by two parties. The log is at ERROR
     * because it warrants a human looking, not a dashboard counter.
     */
    private void handleReuseDetected(RefreshToken replayed, User user, RequestContext context) {
        // Committed in its own transaction. The caller throws immediately after
        // this returns, and a rollback here would mean the family is NOT
        // actually revoked - the replay would still return 401, so the defence
        // would look like it worked while leaving the attacker's rotated token
        // live. See RefreshTokenRevoker.
        int revoked = refreshTokenRevoker.revokeFamilyNow(
                replayed.getFamilyId(), RevokedReason.REUSE_DETECTED);

        log.error("REFRESH TOKEN REUSE DETECTED for user {} (family {}). "
                        + "Revoked {} live token(s) in the family. "
                        + "Replayed token was revoked at {} with reason {}. Request ip={}, userAgent={}",
                user.getEmail(), replayed.getFamilyId(), revoked,
                replayed.getRevokedAt(), replayed.getRevokedReason(),
                context.ipAddress(), context.userAgent());

        auditService.recordSecurityEvent(AuditAction.TOKEN_REUSE_DETECTED,
                user.getId(), user.getEmail(), false,
                "Revoked refresh token replayed; revoked %d token(s) in family %s"
                        .formatted(revoked, replayed.getFamilyId()),
                context);
    }

    // ------------------------------------------------------------------
    // Revocation
    // ------------------------------------------------------------------

    /**
     * Revokes the token behind a logout. Unknown tokens are ignored: logging
     * out is idempotent, and there is nothing useful to tell a caller whose
     * session had already ended.
     */
    @Transactional
    public void revokeByToken(String presentedToken, RevokedReason reason) {
        refreshTokenRepository.findByTokenHash(SecureTokens.hash(presentedToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> token.revoke(reason));
    }

    @Transactional
    public int revokeAllForUser(Long userId, RevokedReason reason) {
        return refreshTokenRepository.revokeAllForUser(userId, reason, Instant.now());
    }

    /** Revokes one device session, identified by row id. */
    @Transactional
    public void revokeSession(Long userId, Long sessionId, RevokedReason reason) {
        RefreshToken token = refreshTokenRepository.findById(sessionId)
                .orElseThrow(() -> new InvalidRefreshTokenException("Session not found."));

        // Ownership check: a session id is a plain sequential number, so
        // without this any authenticated user could revoke another user's
        // sessions by guessing ids. Deliberately reported as "not found"
        // rather than "forbidden", so ids belonging to others are not
        // distinguishable from ids that do not exist.
        if (!token.getUser().getId().equals(userId)) {
            log.warn("User {} attempted to revoke session {} belonging to user {}",
                    userId, sessionId, token.getUser().getId());
            throw new InvalidRefreshTokenException("Session not found.");
        }

        token.revoke(reason);
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<RefreshToken> activeSessions(Long userId) {
        return refreshTokenRepository.findActiveByUserId(userId, Instant.now());
    }

    /** The row id of the session a given plaintext token belongs to, if live. */
    @Transactional(readOnly = true)
    public Long sessionIdOf(String presentedToken) {
        return refreshTokenRepository.findByTokenHash(SecureTokens.hash(presentedToken))
                .map(RefreshToken::getId)
                .orElse(null);
    }
}
