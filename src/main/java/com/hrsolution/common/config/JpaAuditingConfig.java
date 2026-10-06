package com.hrsolution.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Activates the {@code @CreatedDate}/{@code @LastModifiedDate}/{@code @CreatedBy}/
 * {@code @LastModifiedBy} handling on {@code BaseEntity}.
 *
 * <p>Kept in its own class rather than on the main application class so that
 * slice tests ({@code @DataJpaTest}) can opt in with
 * {@code @Import(JpaAuditingConfig.class)} - without it, {@code created_at} is
 * null on insert and the not-null constraint fails inside the test only.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl")
public class JpaAuditingConfig {
}
