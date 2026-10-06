package com.hrsolution.user.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The lockout and token-version rules, tested directly on the entity - no
 * mocks, no Spring context, because they are pure state transitions.
 */
class UserTest {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCKOUT = Duration.ofMinutes(15);

    private User user() {
        User user = new User();
        user.setEmail("asha@example.com");
        user.setFirstName("Asha");
        user.setLastName("Patil");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    @Nested
    @DisplayName("lockout")
    class Lockout {

        @Test
        @DisplayName("locks on the fifth failure, not before")
        void locksOnFifthFailure() {
            User user = user();

            for (int attempt = 1; attempt < MAX_ATTEMPTS; attempt++) {
                user.recordFailedLogin(MAX_ATTEMPTS, LOCKOUT);
                assertThat(user.isLocked())
                        .as("not locked after %d failure(s)", attempt)
                        .isFalse();
            }

            user.recordFailedLogin(MAX_ATTEMPTS, LOCKOUT);
            assertThat(user.isLocked()).isTrue();
            assertThat(user.getFailedAttempts()).isEqualTo(MAX_ATTEMPTS);
        }

        @Test
        @DisplayName("the lock expires on its own, with no unlock job needed")
        void lockSelfClears() {
            User user = user();
            user.setLockedUntil(Instant.now().minusSeconds(1));

            // This is why 'locked' is a timestamp rather than a UserStatus:
            // nothing has to run to release it.
            assertThat(user.isLocked()).isFalse();
            assertThat(user.lockRemainingSeconds()).isZero();
        }

        @Test
        @DisplayName("the failure count is NOT reset when the lock expires")
        void countSurvivesLockExpiry() {
            User user = user();
            for (int i = 0; i < MAX_ATTEMPTS; i++) {
                user.recordFailedLogin(MAX_ATTEMPTS, LOCKOUT);
            }
            // Simulate the 15 minutes passing.
            user.setLockedUntil(Instant.now().minusSeconds(1));
            assertThat(user.isLocked()).isFalse();

            // One more wrong guess locks it again immediately. If the counter
            // reset, a patient attacker would get a fresh batch of five every
            // 15 minutes, indefinitely.
            user.recordFailedLogin(MAX_ATTEMPTS, LOCKOUT);
            assertThat(user.isLocked()).isTrue();
            assertThat(user.getFailedAttempts()).isEqualTo(MAX_ATTEMPTS + 1);
        }

        @Test
        @DisplayName("a successful sign-in clears the counter and the lock")
        void successResetsEverything() {
            User user = user();
            user.recordFailedLogin(MAX_ATTEMPTS, LOCKOUT);
            user.recordFailedLogin(MAX_ATTEMPTS, LOCKOUT);

            user.recordSuccessfulLogin("203.0.113.9");

            assertThat(user.getFailedAttempts()).isZero();
            assertThat(user.getLockedUntil()).isNull();
            assertThat(user.getLastLoginIp()).isEqualTo("203.0.113.9");
            assertThat(user.getLastLoginAt()).isNotNull();
        }

        @Test
        @DisplayName("reports the remaining wait, rounded into the future")
        void reportsRemainingTime() {
            User user = user();
            for (int i = 0; i < MAX_ATTEMPTS; i++) {
                user.recordFailedLogin(MAX_ATTEMPTS, LOCKOUT);
            }
            assertThat(user.lockRemainingSeconds()).isBetween(14L * 60, 15L * 60);
        }
    }

    @Nested
    @DisplayName("token version")
    class TokenVersion {

        @Test
        @DisplayName("changing the password bumps it, invalidating live access tokens")
        void passwordChangeBumpsVersion() {
            User user = user();
            user.setPasswordHash("old-hash");
            int before = user.getTokenVersion();

            user.changePassword("new-hash");

            // Access tokens embed the version they were minted with, so this is
            // what makes a password change cut existing sessions without a
            // token blacklist.
            assertThat(user.getTokenVersion()).isEqualTo(before + 1);
            assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        }

        @Test
        @DisplayName("changing the password also clears any lockout")
        void passwordChangeClearsLockout() {
            User user = user();
            for (int i = 0; i < MAX_ATTEMPTS; i++) {
                user.recordFailedLogin(MAX_ATTEMPTS, LOCKOUT);
            }
            assertThat(user.isLocked()).isTrue();

            user.changePassword("new-hash");

            // A user who has just proved ownership by resetting their password
            // should not then be told to wait 15 minutes.
            assertThat(user.isLocked()).isFalse();
            assertThat(user.getFailedAttempts()).isZero();
        }
    }

    @Nested
    @DisplayName("email verification")
    class EmailVerification {

        @Test
        @DisplayName("verifying activates a PENDING_VERIFICATION account")
        void verificationActivates() {
            User user = user();
            user.setStatus(UserStatus.PENDING_VERIFICATION);

            user.markEmailVerified();

            assertThat(user.isEmailVerified()).isTrue();
            assertThat(user.getEmailVerifiedAt()).isNotNull();
            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("verifying does NOT activate an account awaiting approval")
        void verificationDoesNotBypassApproval() {
            User user = user();
            user.setStatus(UserStatus.PENDING_APPROVAL);

            user.markEmailVerified();

            // A client must still be approved by a human - confirming an email
            // address must not be a way around that.
            assertThat(user.isEmailVerified()).isTrue();
            assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_APPROVAL);
        }

        @Test
        @DisplayName("verifying does not re-enable a disabled account")
        void verificationDoesNotReEnable() {
            User user = user();
            user.setStatus(UserStatus.DISABLED);

            user.markEmailVerified();

            assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
        }
    }

    @Nested
    @DisplayName("derived values")
    class Derived {

        @Test
        @DisplayName("only ACTIVE accounts may sign in")
        void onlyActiveCanLogin() {
            assertThat(UserStatus.ACTIVE.canLogin()).isTrue();
            assertThat(UserStatus.PENDING_VERIFICATION.canLogin()).isFalse();
            assertThat(UserStatus.PENDING_APPROVAL.canLogin()).isFalse();
            assertThat(UserStatus.DISABLED.canLogin()).isFalse();
            assertThat(UserStatus.REJECTED.canLogin()).isFalse();
        }

        @Test
        @DisplayName("full name omits a missing surname without a trailing space")
        void fullName() {
            User user = user();
            assertThat(user.fullName()).isEqualTo("Asha Patil");

            user.setLastName(null);
            assertThat(user.fullName()).isEqualTo("Asha");

            user.setLastName("   ");
            assertThat(user.fullName()).isEqualTo("Asha");
        }

        @Test
        @DisplayName("role and permission names are the union across roles, sorted")
        void rolesAndPermissions() {
            Permission clientRead = permission("CLIENT_READ");
            Permission clientWrite = permission("CLIENT_WRITE");
            Permission payrollRead = permission("PAYROLL_READ");

            Role ops = role("OPERATIONS_MANAGER", clientRead, clientWrite);
            Role accounts = role("ACCOUNTS", payrollRead, clientRead);

            User user = user();
            user.setRoles(new java.util.LinkedHashSet<>(java.util.List.of(ops, accounts)));

            assertThat(user.roleNames())
                    .containsExactly("ACCOUNTS", "OPERATIONS_MANAGER");
            // CLIENT_READ appears in both roles but only once in the union.
            assertThat(user.permissionNames())
                    .containsExactly("CLIENT_READ", "CLIENT_WRITE", "PAYROLL_READ");
            assertThat(user.hasRole(RoleName.ACCOUNTS)).isTrue();
            assertThat(user.hasRole(RoleName.SUPER_ADMIN)).isFalse();
        }

        private Permission permission(String name) {
            Permission permission = new Permission();
            permission.setName(name);
            permission.setModule("TEST");
            return permission;
        }

        private Role role(String name, Permission... permissions) {
            Role role = new Role();
            role.setName(name);
            role.setPermissions(new java.util.LinkedHashSet<>(java.util.List.of(permissions)));
            return role;
        }
    }
}
