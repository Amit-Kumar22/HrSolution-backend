package com.hrsolution.auth.service;

import com.hrsolution.auth.entity.RevokedReason;
import com.hrsolution.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Revocations that must survive the caller rolling back.
 *
 * <p><strong>Why this exists.</strong> The two most important revocations in the
 * system happen on paths that end by throwing - reuse detection and a token
 * presented for a disabled account both reject the request. A rejected request
 * rolls its transaction back, which would discard the revocation along with it.
 *
 * <p>The failure mode that creates is genuinely dangerous and almost invisible:
 * the replay attempt correctly returns 401, so reuse detection <em>looks</em>
 * like it works. But the family is never actually revoked, so an attacker who
 * already rotated the stolen token keeps a live session - which is the entire
 * thing the mechanism exists to prevent. A test that only asserted "replay gives
 * 401" would pass while the defence did nothing.
 *
 * <p>{@link Propagation#REQUIRES_NEW} commits these independently. A separate
 * bean is required, not a method on {@link RefreshTokenService}: a
 * self-invocation would bypass the proxy and silently inherit the caller's
 * transaction, reintroducing the bug in a form that is harder to spot.
 *
 * <p>Same reasoning as {@link LoginAttemptService} and {@code AuditService} -
 * on a failure path, anything worth recording has to be written outside the
 * failing transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenRevoker {

    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * Revokes every live token in a family, committing immediately.
     *
     * @return how many tokens were revoked
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeFamilyNow(String familyId, RevokedReason reason) {
        int revoked = refreshTokenRepository.revokeFamily(familyId, reason, Instant.now());
        log.debug("Revoked {} token(s) in family {} with reason {}", revoked, familyId, reason);
        return revoked;
    }

    /** Revokes all of a user's tokens, committing immediately. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeAllForUserNow(Long userId, RevokedReason reason) {
        int revoked = refreshTokenRepository.revokeAllForUser(userId, reason, Instant.now());
        log.debug("Revoked {} token(s) for user {} with reason {}", revoked, userId, reason);
        return revoked;
    }
}
