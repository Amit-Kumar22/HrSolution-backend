package com.hrsolution.common.config;

import com.hrsolution.common.storage.NoOpVirusScanner;
import com.hrsolution.common.storage.VirusScanner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the file storage layer.
 *
 * <p>{@code StorageService} itself is a plain {@code @Service}
 * ({@code LocalStorageService}); only the pluggable pieces need declaring here.
 */
@Configuration(proxyBeanMethods = false)
public class StorageConfig {

    /**
     * Falls back to the no-op scanner when nothing else provides one.
     *
     * <p>{@code @ConditionalOnMissingBean} has to live on a {@code @Bean} method
     * like this. On a {@code @Component} it is evaluated during component
     * scanning, where the class matches itself as an existing
     * {@link VirusScanner}, the condition fails, and no bean is registered -
     * producing a startup failure that says no {@code VirusScanner} is
     * available while pointing at the only implementation of it.
     *
     * <p>Define any other {@link VirusScanner} bean and this method is skipped.
     */
    @Bean
    @ConditionalOnMissingBean(VirusScanner.class)
    public VirusScanner noOpVirusScanner() {
        return new NoOpVirusScanner();
    }
}
