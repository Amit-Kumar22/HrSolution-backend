package com.hrsolution.common.storage;

import lombok.extern.slf4j.Slf4j;

/**
 * The default scanner, which scans nothing.
 *
 * <p>Deliberately carries no Spring annotations. It is registered by a
 * {@code @Bean} method in {@code StorageConfig} guarded by
 * {@code @ConditionalOnMissingBean}, which is the only arrangement that
 * actually works: putting {@code @ConditionalOnMissingBean} on a
 * {@code @Component} does not do what it looks like it does. That annotation is
 * evaluated against the bean definitions registered so far, and during component
 * scanning the class ends up matching <em>itself</em> as an existing
 * {@link VirusScanner} - so the condition fails and no bean is registered at
 * all. The symptom is a startup failure saying no {@code VirusScanner} is
 * available, pointing at the one class that implements it.
 *
 * <p>To enable real scanning, define any other {@link VirusScanner} bean -
 * ClamAV over its daemon socket, or a cloud scanning API - and this one steps
 * aside automatically.
 *
 * <p>Worth being honest about what the default means: uploads are <strong>not
 * scanned for malware</strong>. The mitigations actually in place are
 * content-based type detection ({@link FileTypeDetector}), a size cap, storage
 * outside any web-served directory, and serving private files only through
 * authenticated endpoints with {@code Content-Disposition: attachment}. Those
 * stop a malicious upload being executed by this application or rendered in a
 * user's browser; they do not stop a user downloading an infected file onto
 * their own machine.
 */
@Slf4j
public class NoOpVirusScanner implements VirusScanner {

    public NoOpVirusScanner() {
        // Logged once at startup rather than per upload, so the fact that
        // scanning is off is visible in the boot log without flooding it later.
        log.info("Virus scanning is not configured - uploads are accepted without a malware scan. "
                + "Content-based type detection, a size cap and authenticated-only downloads are "
                + "still enforced. Provide a VirusScanner bean to enable scanning.");
    }

    @Override
    public void scan(byte[] content, String fileName) {
        // Intentionally empty.
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
