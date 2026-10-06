package com.hrsolution.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled}.
 *
 * <p>Consumers arrive in later phases: purging expired refresh tokens (Phase 2),
 * and the 60/30/7-day expiry reminders for licences, client contracts and worker
 * documents (Phase 10).
 *
 * <p>Scheduled jobs are disabled in tests via {@code app.scheduling.enabled} so
 * that a Testcontainers run does not fire background work mid-assertion.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
