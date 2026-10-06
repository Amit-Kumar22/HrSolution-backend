package com.hrsolution.auth.scheduler;

import com.hrsolution.auth.repository.PasswordResetTokenRepository;
import com.hrsolution.auth.repository.RefreshTokenRepository;
import com.hrsolution.auth.repository.VerificationTokenRepository;
import com.hrsolution.common.config.AuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Deletes expired tokens so the three token tables do not grow without bound.
 *
 * <p>Refresh tokens are kept for {@code app.auth.refresh.retention} <em>past</em>
 * their expiry rather than deleted as soon as they lapse. The rotation chain is
 * the evidence trail for a suspected token theft, and an incident is usually
 * investigated days after the fact - purging on expiry would destroy exactly
 * the records needed.
 *
 * <p>Runs hourly. The delete is a single bulk statement against an index on
 * {@code expires_at}.
 *
 * <p>Note for a scaled-out deployment: every instance runs this, so the job
 * must stay idempotent. It is - deleting rows that another instance has already
 * removed simply affects zero rows. Should any future job not be idempotent, it
 * needs a distributed lock (ShedLock or similar) rather than this plain
 * {@code @Scheduled}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class TokenPurgeJob {

    private final RefreshTokenRepository refreshTokenRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final AuthProperties authProperties;

    /** Hourly, offset so it does not coincide with other top-of-hour work. */
    @Scheduled(cron = "0 7 * * * *")
    @Transactional
    public void purgeExpiredTokens() {
        Instant now = Instant.now();

        Instant refreshCutoff = now.minus(authProperties.getRefresh().getRetention());
        int refreshDeleted = refreshTokenRepository.deleteExpiredBefore(refreshCutoff);

        // One-shot tokens carry no forensic value once spent, so they go as
        // soon as they are a day past expiry.
        Instant shortLivedCutoff = now.minus(Duration.ofDays(1));
        int verificationDeleted = verificationTokenRepository.deleteExpiredBefore(shortLivedCutoff);
        int resetDeleted = passwordResetTokenRepository.deleteExpiredBefore(shortLivedCutoff);

        if (refreshDeleted + verificationDeleted + resetDeleted > 0) {
            log.info("Token purge removed {} refresh, {} verification and {} password-reset row(s)",
                    refreshDeleted, verificationDeleted, resetDeleted);
        }
    }
}
