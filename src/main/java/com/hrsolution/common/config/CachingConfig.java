package com.hrsolution.common.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Turns on {@code @Cacheable}/{@code @CacheEvict} support.
 *
 * <p>No {@code CacheManager} bean is declared on purpose. The provider is chosen
 * by {@code spring.cache.type} in the profile properties - Caffeine locally and
 * in dev, with Redis available in prod by switching that one property and
 * adding the Redis starter. Declaring a manager here would override that and
 * pin every environment to Caffeine.
 *
 * <p>Cache names and their eviction policy live in {@code application.properties}
 * under {@code spring.cache.cache-names} and {@code spring.cache.caffeine.spec}.
 */
@Configuration
@EnableCaching
public class CachingConfig {

    /** Company profile - read on nearly every page, changed a few times a year. */
    public static final String COMPANY_SETTINGS = "companySettings";
}
