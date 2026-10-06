package com.hrsolution.auth.service;

import com.hrsolution.common.config.AuthProperties;
import com.hrsolution.user.entity.User;
import com.hrsolution.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Persists failed sign-in attempts and applies the lockout.
 *
 * <p><strong>Why this is a separate bean.</strong> A failed sign-in ends by
 * throwing, which rolls the caller's transaction back. Incrementing
 * {@code failed_attempts} inside that transaction therefore discards the
 * increment along with everything else - the counter stays at zero forever and
 * the account never locks, however many passwords are tried. The lockout looks
 * implemented and defends nothing.
 *
 * <p>{@link Propagation#REQUIRES_NEW} gives the increment its own transaction,
 * which commits regardless of the caller rolling back. It has to be a separate
 * bean rather than a method on {@code AuthService}, because a self-invocation
 * would bypass the proxy and silently inherit the caller's transaction - the
 * same bug with a more convincing disguise.
 *
 * <p>This mirrors the reasoning in {@code AuditService}: the records that matter
 * most are written on the failure paths, so they cannot share the failing
 * transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final UserRepository userRepository;
    private final AuthProperties authProperties;

    /** Outcome of recording a failure, for the caller's audit message. */
    public record FailureOutcome(int failedAttempts, int maxAttempts, boolean nowLocked) {
    }

    /**
     * Increments the failure counter, locking the account at the threshold.
     *
     * <p>The user is reloaded inside the new transaction: the caller's instance
     * belongs to a transaction that is about to roll back, so mutating it here
     * would achieve nothing.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FailureOutcome recordFailure(Long userId) {
        AuthProperties.Lockout lockout = authProperties.getLockout();

        Optional<User> maybeUser = userRepository.findById(userId);
        if (maybeUser.isEmpty()) {
            // Deleted between the credential check and here. Nothing to record.
            return new FailureOutcome(0, lockout.getMaxAttempts(), false);
        }

        User user = maybeUser.get();
        user.recordFailedLogin(lockout.getMaxAttempts(), lockout.getDuration());
        userRepository.save(user);

        boolean nowLocked = user.isLocked();
        if (nowLocked) {
            log.warn("Account {} locked until {} after {} failed attempt(s)",
                    user.getEmail(), user.getLockedUntil(), user.getFailedAttempts());
        }

        return new FailureOutcome(user.getFailedAttempts(), lockout.getMaxAttempts(), nowLocked);
    }

    /**
     * Clears the counter and records the successful sign-in.
     *
     * <p>Also {@link Propagation#REQUIRES_NEW}, for symmetry and durability: the
     * "last signed in" record should survive even if something later in the
     * request fails and rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(Long userId, String ipAddress) {
        userRepository.findById(userId).ifPresent(user -> {
            user.recordSuccessfulLogin(ipAddress);
            userRepository.save(user);
        });
    }
}
