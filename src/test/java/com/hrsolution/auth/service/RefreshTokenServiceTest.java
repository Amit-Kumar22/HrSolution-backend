package com.hrsolution.auth.service;

import com.hrsolution.audit.entity.AuditAction;
import com.hrsolution.audit.service.AuditService;
import com.hrsolution.auth.entity.RefreshToken;
import com.hrsolution.auth.entity.RevokedReason;
import com.hrsolution.auth.repository.RefreshTokenRepository;
import com.hrsolution.common.config.AuthProperties;
import com.hrsolution.common.security.SecureTokens;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.user.entity.User;
import com.hrsolution.user.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Rotation and reuse detection - the security-critical half of Phase 2.
 *
 * <p>These assertions encode the threat model, so read a failure here as "the
 * system no longer defends against token theft", not "a test needs updating".
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefreshTokenServiceTest {

    private static final Long USER_ID = 42L;
    private static final String FAMILY_ID = "family-0001";

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private RefreshTokenRevoker refreshTokenRevoker;

    private AuthProperties authProperties;
    private RefreshTokenService refreshTokenService;
    private RequestContext context;

    @BeforeEach
    void setUp() {
        authProperties = new AuthProperties();
        refreshTokenService = new RefreshTokenService(
                refreshTokenRepository, authProperties, auditService, refreshTokenRevoker);
        context = new RequestContext("203.0.113.7", "JUnit/1.0", "corr-1");

        // save() assigns an id, mirroring the real IDENTITY column.
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            if (token.getId() == null) {
                token.setId(999L);
            }
            return token;
        });
    }

    private User activeUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("asha@example.com");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private RefreshToken liveToken(String plaintext, User user) {
        RefreshToken token = new RefreshToken();
        token.setId(1L);
        token.setUser(user);
        token.setTokenHash(SecureTokens.hash(plaintext));
        token.setFamilyId(FAMILY_ID);
        token.setExpiresAt(Instant.now().plus(Duration.ofDays(7)));
        return token;
    }

    // ==================================================================

    @Nested
    @DisplayName("issuing")
    class Issuing {

        @Test
        @DisplayName("stores only the hash, never the token itself")
        void storesOnlyTheHash() {
            User user = activeUser();

            RefreshTokenService.IssuedRefreshToken issued =
                    refreshTokenService.issueNewFamily(user, false, context);

            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository).save(captor.capture());
            RefreshToken saved = captor.getValue();

            // The whole point: a database leak must not yield a usable token.
            assertThat(saved.getTokenHash()).isNotEqualTo(issued.plaintext());
            assertThat(saved.getTokenHash()).isEqualTo(SecureTokens.hash(issued.plaintext()));
            assertThat(saved.getTokenHash()).hasSize(64);
        }

        @Test
        @DisplayName("records the device so it can be shown on the sessions screen")
        void recordsDevice() {
            refreshTokenService.issueNewFamily(activeUser(), false, context);

            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository).save(captor.capture());

            assertThat(captor.getValue().getIpAddress()).isEqualTo("203.0.113.7");
            assertThat(captor.getValue().getUserAgent()).isEqualTo("JUnit/1.0");
        }

        @Test
        @DisplayName("remember-me extends the lifetime from 7 days to 30")
        void rememberMeExtendsLifetime() {
            RefreshTokenService.IssuedRefreshToken ordinary =
                    refreshTokenService.issueNewFamily(activeUser(), false, context);
            RefreshTokenService.IssuedRefreshToken remembered =
                    refreshTokenService.issueNewFamily(activeUser(), true, context);

            Duration ordinaryLife = Duration.between(Instant.now(), ordinary.expiresAt());
            Duration rememberedLife = Duration.between(Instant.now(), remembered.expiresAt());

            assertThat(ordinaryLife.toDays()).isEqualTo(6);      // 7 days, minus a moment
            assertThat(rememberedLife.toDays()).isEqualTo(29);   // 30 days, minus a moment
        }

        @Test
        @DisplayName("each login starts a separate family")
        void eachLoginStartsItsOwnFamily() {
            String first = refreshTokenService.issueNewFamily(activeUser(), false, context).familyId();
            String second = refreshTokenService.issueNewFamily(activeUser(), false, context).familyId();

            // Otherwise detecting theft on one device would log out every device.
            assertThat(first).isNotEqualTo(second);
        }

        @Test
        @DisplayName("issues a different token value every time")
        void tokensAreUnique() {
            String first = refreshTokenService.issueNewFamily(activeUser(), false, context).plaintext();
            String second = refreshTokenService.issueNewFamily(activeUser(), false, context).plaintext();

            assertThat(first).isNotEqualTo(second);
            // 32 random bytes, URL-safe Base64 without padding.
            assertThat(first).hasSize(43);
        }
    }

    @Nested
    @DisplayName("rotation")
    class Rotation {

        @Test
        @DisplayName("consumes the presented token and links it to its successor")
        void consumesAndLinks() {
            User user = activeUser();
            String presented = SecureTokens.generate();
            RefreshToken existing = liveToken(presented, user);

            when(refreshTokenRepository.findByTokenHash(SecureTokens.hash(presented)))
                    .thenReturn(Optional.of(existing));

            RefreshTokenService.RotationResult result =
                    refreshTokenService.rotate(presented, context);

            assertThat(existing.isRevoked()).isTrue();
            assertThat(existing.getRevokedReason()).isEqualTo(RevokedReason.ROTATED);
            // The chain is the evidence trail used when investigating a theft.
            assertThat(existing.getReplacedByTokenId()).isEqualTo(result.token().tokenId());
            assertThat(result.userId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("the successor stays in the same family")
        void successorKeepsFamily() {
            User user = activeUser();
            String presented = SecureTokens.generate();
            when(refreshTokenRepository.findByTokenHash(anyString()))
                    .thenReturn(Optional.of(liveToken(presented, user)));

            RefreshTokenService.RotationResult result =
                    refreshTokenService.rotate(presented, context);

            assertThat(result.token().familyId()).isEqualTo(FAMILY_ID);
        }

        @Test
        @DisplayName("carries remember-me across the rotation")
        void carriesRememberMe() {
            User user = activeUser();
            String presented = SecureTokens.generate();
            RefreshToken existing = liveToken(presented, user);
            existing.setRememberMe(true);

            when(refreshTokenRepository.findByTokenHash(anyString()))
                    .thenReturn(Optional.of(existing));

            RefreshTokenService.RotationResult result =
                    refreshTokenService.rotate(presented, context);

            // Without this, a 30-day session silently became a 7-day one on its
            // first refresh.
            assertThat(Duration.between(Instant.now(), result.token().expiresAt()).toDays())
                    .isEqualTo(29);
        }

        @Test
        @DisplayName("a token matching no stored hash is rejected")
        void unknownTokenRejected() {
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> refreshTokenService.rotate("fabricated", context))
                    .isInstanceOf(RefreshTokenService.InvalidRefreshTokenException.class)
                    .hasMessageContaining("sign in again");

            // Nothing to revoke, and no family to cut.
            verify(refreshTokenRevoker, never()).revokeFamilyNow(anyString(), any());
        }

        @Test
        @DisplayName("an expired token is rejected and consumed")
        void expiredTokenRejected() {
            User user = activeUser();
            String presented = SecureTokens.generate();
            RefreshToken expired = liveToken(presented, user);
            expired.setExpiresAt(Instant.now().minus(Duration.ofMinutes(1)));

            when(refreshTokenRepository.findByTokenHash(anyString()))
                    .thenReturn(Optional.of(expired));

            assertThatThrownBy(() -> refreshTokenService.rotate(presented, context))
                    .isInstanceOf(RefreshTokenService.InvalidRefreshTokenException.class);

            assertThat(expired.isRevoked()).isTrue();
            // An expired token is not evidence of theft, so the family survives.
            verify(refreshTokenRevoker, never()).revokeFamilyNow(anyString(), any());
        }

        @Test
        @DisplayName("a token for a disabled account is rejected and all its sessions cut")
        void disabledAccountRejected() {
            User user = activeUser();
            user.setStatus(UserStatus.DISABLED);
            String presented = SecureTokens.generate();

            when(refreshTokenRepository.findByTokenHash(anyString()))
                    .thenReturn(Optional.of(liveToken(presented, user)));

            assertThatThrownBy(() -> refreshTokenService.rotate(presented, context))
                    .isInstanceOf(RefreshTokenService.InvalidRefreshTokenException.class)
                    .hasMessageContaining("not active");

            // A disabled user must not be able to keep minting access tokens.
            verify(refreshTokenRevoker)
                    .revokeAllForUserNow(eq(USER_ID), eq(RevokedReason.ADMIN_REVOKED));
        }
    }

    @Nested
    @DisplayName("reuse detection")
    class ReuseDetection {

        @Test
        @DisplayName("replaying a consumed token revokes the whole family")
        void replayRevokesWholeFamily() {
            User user = activeUser();
            String stolen = SecureTokens.generate();

            // The state after the victim has already refreshed once.
            RefreshToken alreadyRotated = liveToken(stolen, user);
            alreadyRotated.revoke(RevokedReason.ROTATED);
            alreadyRotated.setReplacedByTokenId(2L);

            when(refreshTokenRepository.findByTokenHash(SecureTokens.hash(stolen)))
                    .thenReturn(Optional.of(alreadyRotated));
            when(refreshTokenRevoker.revokeFamilyNow(eq(FAMILY_ID), any())).thenReturn(2);

            assertThatThrownBy(() -> refreshTokenService.rotate(stolen, context))
                    .isInstanceOf(RefreshTokenService.InvalidRefreshTokenException.class)
                    .hasMessageContaining("no longer valid");

            // The crux: revoking only the replayed token would leave the
            // attacker holding a live successor.
            // Through the revoker, which commits in its own transaction - this
            // method throws next, and a rollback would undo the revocation.
            verify(refreshTokenRevoker)
                    .revokeFamilyNow(eq(FAMILY_ID), eq(RevokedReason.REUSE_DETECTED));
            // And no replacement is handed out.
            verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("records a TOKEN_REUSE_DETECTED audit event")
        void auditsTheDetection() {
            User user = activeUser();
            String stolen = SecureTokens.generate();
            RefreshToken alreadyRotated = liveToken(stolen, user);
            alreadyRotated.revoke(RevokedReason.ROTATED);

            when(refreshTokenRepository.findByTokenHash(anyString()))
                    .thenReturn(Optional.of(alreadyRotated));

            assertThatThrownBy(() -> refreshTokenService.rotate(stolen, context))
                    .isInstanceOf(RefreshTokenService.InvalidRefreshTokenException.class);

            verify(auditService).recordSecurityEvent(
                    eq(AuditAction.TOKEN_REUSE_DETECTED), eq(USER_ID), eq(user.getEmail()),
                    eq(false), anyString(), eq(context));
        }

        @Test
        @DisplayName("a token revoked by logout is also treated as reuse when replayed")
        void replayAfterLogoutIsAlsoReuse() {
            User user = activeUser();
            String presented = SecureTokens.generate();
            RefreshToken loggedOut = liveToken(presented, user);
            loggedOut.revoke(RevokedReason.LOGOUT);

            when(refreshTokenRepository.findByTokenHash(anyString()))
                    .thenReturn(Optional.of(loggedOut));

            assertThatThrownBy(() -> refreshTokenService.rotate(presented, context))
                    .isInstanceOf(RefreshTokenService.InvalidRefreshTokenException.class);

            // Any revoked token coming back means two parties held it. The
            // reason it was revoked does not change that.
            // Through the revoker, which commits in its own transaction - this
            // method throws next, and a rollback would undo the revocation.
            verify(refreshTokenRevoker)
                    .revokeFamilyNow(eq(FAMILY_ID), eq(RevokedReason.REUSE_DETECTED));
        }
    }

    @Nested
    @DisplayName("revocation")
    class Revocation {

        @Test
        @DisplayName("revoke keeps the original reason if called twice")
        void revokeIsIdempotentAndKeepsFirstReason() {
            RefreshToken token = liveToken(SecureTokens.generate(), activeUser());

            token.revoke(RevokedReason.LOGOUT);
            Instant firstRevokedAt = token.getRevokedAt();
            token.revoke(RevokedReason.REUSE_DETECTED);

            // A token ended by logout should still read as LOGOUT even if a
            // later family-wide purge sweeps over it.
            assertThat(token.getRevokedReason()).isEqualTo(RevokedReason.LOGOUT);
            assertThat(token.getRevokedAt()).isEqualTo(firstRevokedAt);
        }

        @Test
        @DisplayName("logging out with an unknown token is silently accepted")
        void logoutWithUnknownTokenIsAccepted() {
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            // The caller's goal - end my session - is already met.
            refreshTokenService.revokeByToken("stale-token", RevokedReason.LOGOUT);
        }

        @Test
        @DisplayName("a user cannot revoke another user's session")
        void cannotRevokeSomeoneElsesSession() {
            User otherUser = new User();
            otherUser.setId(777L);
            otherUser.setEmail("someone.else@example.com");

            RefreshToken theirToken = liveToken(SecureTokens.generate(), otherUser);
            when(refreshTokenRepository.findById(5L)).thenReturn(Optional.of(theirToken));

            // Session ids are sequential, so without the ownership check any
            // signed-in user could log everyone else out by guessing ids.
            assertThatThrownBy(() -> refreshTokenService
                    .revokeSession(USER_ID, 5L, RevokedReason.SESSION_REVOKED))
                    .isInstanceOf(RefreshTokenService.InvalidRefreshTokenException.class)
                    // Reported as "not found", so a foreign id is
                    // indistinguishable from one that does not exist.
                    .hasMessageContaining("not found");

            assertThat(theirToken.isRevoked()).isFalse();
        }

        @Test
        @DisplayName("a user can revoke their own session")
        void canRevokeOwnSession() {
            RefreshToken ownToken = liveToken(SecureTokens.generate(), activeUser());
            when(refreshTokenRepository.findById(5L)).thenReturn(Optional.of(ownToken));

            refreshTokenService.revokeSession(USER_ID, 5L, RevokedReason.SESSION_REVOKED);

            assertThat(ownToken.isRevoked()).isTrue();
            assertThat(ownToken.getRevokedReason()).isEqualTo(RevokedReason.SESSION_REVOKED);
        }

        @Test
        @DisplayName("revokeAllForUser delegates to a single bulk update")
        void revokeAllIsBulk() {
            when(refreshTokenRepository.revokeAllForUser(anyLong(), any(), any())).thenReturn(3);

            int revoked = refreshTokenService
                    .revokeAllForUser(USER_ID, RevokedReason.PASSWORD_CHANGED);

            assertThat(revoked).isEqualTo(3);
            verify(refreshTokenRepository)
                    .revokeAllForUser(eq(USER_ID), eq(RevokedReason.PASSWORD_CHANGED), any());
        }
    }

    @Nested
    @DisplayName("token hashing")
    class Hashing {

        @Test
        @DisplayName("hashing is deterministic, so lookup by hash works")
        void deterministic() {
            String token = SecureTokens.generate();
            assertThat(SecureTokens.hash(token)).isEqualTo(SecureTokens.hash(token));
        }

        @Test
        @DisplayName("different tokens hash differently")
        void distinct() {
            assertThat(SecureTokens.hash(SecureTokens.generate()))
                    .isNotEqualTo(SecureTokens.hash(SecureTokens.generate()));
        }

        @Test
        @DisplayName("comparison is null-safe")
        void comparisonNullSafe() {
            String hash = SecureTokens.hash("x");
            assertThat(SecureTokens.hashesMatch(hash, hash)).isTrue();
            assertThat(SecureTokens.hashesMatch(hash, null)).isFalse();
            assertThat(SecureTokens.hashesMatch(null, null)).isFalse();
        }
    }
}
