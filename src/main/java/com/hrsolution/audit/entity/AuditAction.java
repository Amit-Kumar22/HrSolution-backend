package com.hrsolution.audit.entity;

/**
 * Auditable actions.
 *
 * <p>Phase 2 populates the security events. The {@code CREATE}/{@code UPDATE}/
 * {@code DELETE} entries are used from Phase 4 onward for entity change
 * tracking, where {@code oldValues}/{@code newValues} carry the JSON diff.
 *
 * <p>Never rename a constant: existing rows would become unreadable.
 */
public enum AuditAction {

    // ---------- Authentication ----------
    LOGIN_SUCCESS,
    /** Wrong password, or an email with no account. Recorded either way. */
    LOGIN_FAILURE,
    /** Correct credentials but the account is not in a usable state. */
    LOGIN_BLOCKED,
    ACCOUNT_LOCKED,
    LOGOUT,
    LOGOUT_ALL,
    TOKEN_REFRESHED,
    /**
     * A revoked refresh token was replayed. Treated as theft: the whole family
     * is revoked. Worth alerting on.
     */
    TOKEN_REUSE_DETECTED,
    SESSION_REVOKED,

    // ---------- Registration and credentials ----------
    REGISTERED,
    EMAIL_VERIFIED,
    VERIFICATION_RESENT,
    PASSWORD_CHANGED,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_COMPLETED,

    // ---------- Administration ----------
    USER_CREATED,
    USER_UPDATED,
    USER_ENABLED,
    USER_DISABLED,
    ROLE_ASSIGNED,
    ROLE_PERMISSIONS_CHANGED,
    CLIENT_APPROVED,
    CLIENT_REJECTED,

    // ---------- Generic entity changes, from Phase 4 ----------
    CREATE,
    UPDATE,
    DELETE
}
