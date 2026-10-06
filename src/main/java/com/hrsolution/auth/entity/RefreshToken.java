package com.hrsolution.auth.entity;

import com.hrsolution.common.domain.BaseEntity;
import com.hrsolution.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One issued refresh token. Also the record of an active device session.
 *
 * <p>Tokens form a <strong>family</strong>: every token minted from one login
 * shares a {@link #familyId}, and each rotation links the old row to its
 * successor via {@link #replacedByTokenId}. That chain is what makes theft
 * detectable - see {@link #isUsable()} and the reuse handling in
 * {@code RefreshTokenService}.
 *
 * <p>Only the SHA-256 {@link #tokenHash} is stored. The plaintext value exists
 * solely in the client's cookie, so a database leak yields nothing usable.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
public class RefreshToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Hex-encoded SHA-256 of the opaque token. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    /** Shared across one login's whole rotation chain. */
    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    /**
     * The token issued when this one was rotated. A plain id rather than an
     * association: the chain is only read for diagnostics, and mapping it as a
     * self-referencing entity would invite accidental lazy-loading cascades
     * down a long chain.
     */
    @Column(name = "replaced_by_token_id")
    private Long replacedByTokenId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoked_reason", length = 50)
    private RevokedReason revokedReason;

    /** Chosen at login; extends the lifetime from 7 days to 30. */
    @Column(name = "remember_me", nullable = false)
    private boolean rememberMe = false;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /** Raw User-Agent, shown on the active sessions screen. */
    @Column(name = "user_agent", length = 300)
    private String userAgent;

    // ------------------------------------------------------------------

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    /** A token may be exchanged exactly once, before it expires. */
    public boolean isUsable() {
        return !isRevoked() && !isExpired();
    }

    public void revoke(RevokedReason reason) {
        // Keep the original reason: a token revoked by logout and later caught
        // in a family-wide purge should still read as LOGOUT, since that is
        // what actually ended it.
        if (this.revokedAt == null) {
            this.revokedAt = Instant.now();
            this.revokedReason = reason;
        }
    }
}
