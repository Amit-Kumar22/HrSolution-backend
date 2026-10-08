package com.hrsolution.settings.service;

import com.hrsolution.common.config.CachingConfig;
import com.hrsolution.common.storage.FileTypeDetector;
import com.hrsolution.document.entity.DocumentOwnerType;
import com.hrsolution.document.entity.StoredDocument;
import com.hrsolution.document.service.DocumentService;
import com.hrsolution.settings.dto.CompanySettingsResponse;
import com.hrsolution.settings.dto.PublicCompanyProfileResponse;
import com.hrsolution.settings.dto.UpdateCompanySettingsRequest;
import com.hrsolution.settings.entity.CompanySettings;
import com.hrsolution.settings.mapper.CompanySettingsMapper;
import com.hrsolution.settings.repository.CompanySettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Reads and updates the single company profile row.
 *
 * <p>Reads are cached: this row is consulted when rendering the marketing site,
 * every outbound email, every payslip and every invoice, and it changes a
 * handful of times a year.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanySettingsService {

    /**
     * The company profile is a singleton row with a fixed primary key, seeded by
     * Flyway migration {@code V1}. Pinning the id keeps every read a primary-key
     * lookup and makes it impossible to end up with two competing profiles.
     */
    public static final long SINGLETON_ID = 1L;

    private final CompanySettingsRepository companySettingsRepository;
    private final CompanySettingsMapper companySettingsMapper;
    private final DocumentService documentService;

    @Transactional(readOnly = true)
    @Cacheable(CachingConfig.COMPANY_SETTINGS)
    public CompanySettingsResponse get() {
        return companySettingsMapper.toResponse(load());
    }

    @Transactional
    @CacheEvict(cacheNames = CachingConfig.COMPANY_SETTINGS, allEntries = true)
    public CompanySettingsResponse update(UpdateCompanySettingsRequest request) {
        CompanySettings settings = load();
        companySettingsMapper.applyUpdate(request, settings);

        // saveAndFlush, not save. The @LastModifiedDate / @LastModifiedBy columns
        // are written by AuditingEntityListener on @PreUpdate, which only runs at
        // flush. With a plain save() the flush happens at transaction commit -
        // after this method has already mapped the response - so the returned
        // updatedAt/updatedBy would be the previous values while the database
        // held the new ones. Flushing here makes the response match the row.
        CompanySettings saved = companySettingsRepository.saveAndFlush(settings);

        log.info("Company profile updated: legalName='{}', gstin='{}'",
                saved.getLegalName(), saved.getGstin());
        return companySettingsMapper.toResponse(saved);
    }

    /**
     * The trimmed profile for the public website.
     *
     * <p>Cached under its own key alongside the full profile - the public site
     * is the highest-traffic consumer, and it is anonymous traffic, so it should
     * not reach the database on every page view.
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CachingConfig.COMPANY_SETTINGS, key = "'public'")
    public PublicCompanyProfileResponse getPublicProfile() {
        return companySettingsMapper.toPublicProfile(load());
    }

    /**
     * Replaces the company logo.
     *
     * <p>Its own endpoint rather than a field on the profile form, so saving a
     * changed phone number cannot blank the logo - and so the upload can be
     * validated as an image by content rather than by file name.
     */
    @Transactional
    @CacheEvict(cacheNames = CachingConfig.COMPANY_SETTINGS, allEntries = true)
    public CompanySettingsResponse uploadLogo(MultipartFile file) {
        CompanySettings settings = load();

        StoredDocument document = documentService.upload(
                file, DocumentOwnerType.COMPANY_LOGO, null, FileTypeDetector.IMAGES);

        settings.setLogoPath(document.getStorageKey());
        log.info("Company logo replaced; stored as {}", document.getStorageKey());

        return companySettingsMapper.toResponse(companySettingsRepository.saveAndFlush(settings));
    }

    private CompanySettings load() {
        return companySettingsRepository.findById(SINGLETON_ID)
                // Not a 404: the caller did nothing wrong. The seed row is part of
                // the schema contract, so a missing row means the database was
                // tampered with or a migration was rolled back by hand.
                .orElseThrow(() -> new IllegalStateException(
                        "company_settings row id=%d is missing. It is seeded by Flyway migration V1 - "
                                .formatted(SINGLETON_ID)
                                + "check that migrations ran against this database."));
    }
}
