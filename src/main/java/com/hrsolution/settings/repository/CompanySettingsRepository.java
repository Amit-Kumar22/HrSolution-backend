package com.hrsolution.settings.repository;

import com.hrsolution.settings.entity.CompanySettings;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for the single-row company profile.
 *
 * <p>No finder methods are needed: the row is always fetched by the fixed id
 * held in {@code CompanySettingsService.SINGLETON_ID}.
 */
public interface CompanySettingsRepository extends JpaRepository<CompanySettings, Long> {
}
