package com.hrsolution.auth.entity;

/** Why a refresh token stopped being valid. Recorded for forensics. */
public enum RevokedReason {

    /** Normal rotation: consumed by a successful {@code /auth/refresh}. */
    ROTATED,

    /** The user logged out of this device. */
    LOGOUT,

    /** The user logged out of every device. */
    LOGOUT_ALL,

    /**
     * An already-revoked token was presented again, so the token had leaked.
     * The entire family is revoked under this reason.
     */
    REUSE_DETECTED,

    /** Password was changed or reset, so every session was cut. */
    PASSWORD_CHANGED,

    /** An administrator disabled the account or revoked its sessions. */
    ADMIN_REVOKED,

    /** The user revoked this specific device from the sessions screen. */
    SESSION_REVOKED
}
