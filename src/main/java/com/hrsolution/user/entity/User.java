package com.hrsolution.user.entity;

import com.hrsolution.common.domain.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * A login account. Every human who can authenticate has exactly one of these,
 * whichever role they hold.
 *
 * <p>Login-related state transitions live on the entity rather than in the
 * service, so that the lockout rules exist in one place and can be unit-tested
 * without a Spring context.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends SoftDeletableEntity {

    @Column(name = "email", nullable = false, length = 180)
    private String email;

    /** BCrypt hash, strength 12. Never logged, never returned by any DTO. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "phone", length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private UserStatus status = UserStatus.PENDING_VERIFICATION;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    /**
     * Bumped to invalidate every access token already issued to this user.
     * Access tokens carry the value they were minted with, and each request
     * compares it against this column, so a mismatch rejects the token without
     * needing a blacklist. Incremented on password change, role change and
     * account disable.
     */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "last_login_ip", length = 45)
    private String lastLoginIp;

    /** When the user ticked the consent box, as required by the DPDP Act. */
    @Column(name = "consent_given_at")
    private Instant consentGivenAt;

    /**
     * Company name typed by a self-registering client, held until an admin
     * approves the account. Phase 4 creates the real {@code clients} row on
     * approval, after which this is only history.
     */
    @Column(name = "pending_company_name", length = 200)
    private String pendingCompanyName;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new LinkedHashSet<>();

    // ------------------------------------------------------------------
    // Derived values
    // ------------------------------------------------------------------

    public String fullName() {
        return lastName == null || lastName.isBlank() ? firstName : firstName + " " + lastName;
    }

    /** True while a lockout is still in force. Self-clearing as time passes. */
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    /**
     * Role names, sorted, for the {@code roles} token claim.
     * Requires {@code roles} to have been fetched.
     */
    public Set<String> roleNames() {
        Set<String> names = new TreeSet<>();
        roles.forEach(role -> names.add(role.getName()));
        return names;
    }

    /**
     * The union of permissions across all the user's roles, sorted, for the
     * {@code permissions} token claim. Requires {@code roles} and their
     * {@code permissions} to have been fetched - see
     * {@code UserRepository.findByEmailWithRoles}.
     */
    public Set<String> permissionNames() {
        Set<String> names = new TreeSet<>();
        roles.forEach(role -> role.getPermissions()
                .forEach(permission -> names.add(permission.getName())));
        return names;
    }

    public boolean hasRole(RoleName roleName) {
        return roles.stream().anyMatch(role -> role.getName().equals(roleName.name()));
    }

    // ------------------------------------------------------------------
    // Login state transitions
    // ------------------------------------------------------------------

    /**
     * Records a failed password attempt and locks the account once the
     * threshold is reached.
     *
     * <p>The counter is not reset when the lock expires. That is deliberate: it
     * means the attempt after an expired lock locks the account again
     * immediately rather than granting a fresh batch of five, so a patient
     * attacker cannot keep guessing five at a time forever. Only a successful
     * login clears it.
     */
    public void recordFailedLogin(int maxAttempts, Duration lockoutDuration) {
        this.failedAttempts = this.failedAttempts + 1;
        if (this.failedAttempts >= maxAttempts) {
            this.lockedUntil = Instant.now().plus(lockoutDuration);
        }
    }

    public void recordSuccessfulLogin(String ipAddress) {
        this.failedAttempts = 0;
        this.lockedUntil = null;
        this.lastLoginAt = Instant.now();
        this.lastLoginIp = ipAddress;
    }

    /** Seconds until the lockout expires, or 0 when not locked. */
    public long lockRemainingSeconds() {
        if (!isLocked()) {
            return 0L;
        }
        return Duration.between(Instant.now(), lockedUntil).toSeconds();
    }

    public void markEmailVerified() {
        this.emailVerified = true;
        this.emailVerifiedAt = Instant.now();
        if (this.status == UserStatus.PENDING_VERIFICATION) {
            this.status = UserStatus.ACTIVE;
        }
    }

    /** Invalidates every outstanding access token for this user. */
    public void incrementTokenVersion() {
        this.tokenVersion = this.tokenVersion + 1;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        // Any session that may have been opened with the old password is no
        // longer trustworthy.
        incrementTokenVersion();
        this.failedAttempts = 0;
        this.lockedUntil = null;
    }
}
