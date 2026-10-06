package com.hrsolution.user.entity;

/**
 * Lifecycle of a user account.
 *
 * <p>Note that <em>locked</em> is deliberately not a status: a lockout is
 * temporary and self-clearing, tracked by {@code lockedUntil}. Making it a
 * status would require a scheduled job to unlock accounts, and would lose the
 * status the account had before it was locked.
 */
public enum UserStatus {

    /** Self-registered candidate; must click the emailed verification link. */
    PENDING_VERIFICATION,

    /** Self-registered client company; waits for an admin to approve it. */
    PENDING_APPROVAL,

    /** Normal, usable account. */
    ACTIVE,

    /** Switched off by an administrator. Can be re-enabled. */
    DISABLED,

    /** Client registration an admin declined. Terminal. */
    REJECTED;

    /**
     * Whether an account in this state may obtain tokens.
     *
     * <p>Only {@link #ACTIVE} can. The others each produce a distinct,
     * actionable message at the login endpoint - "verify your email" is useful
     * feedback, whereas a generic failure would leave the user stuck.
     */
    public boolean canLogin() {
        return this == ACTIVE;
    }
}
