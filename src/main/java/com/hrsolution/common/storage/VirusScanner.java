package com.hrsolution.common.storage;

/**
 * Hook for malware scanning of uploads.
 *
 * <p>{@link NoOpVirusScanner} is the default and scans nothing. The interface
 * exists now so that adding a real scanner later - ClamAV over its daemon
 * socket, or a cloud scanning API - is a new bean rather than a change to
 * {@code DocumentService}.
 *
 * <p>Worth being honest about what the default means: uploads are <em>not</em>
 * scanned. The mitigations actually in place are content-based type detection
 * (see {@link FileTypeDetector}), a size cap, and serving private files only
 * through authenticated endpoints with
 * {@code Content-Disposition: attachment}. Those stop a malicious upload being
 * executed <em>by this application or its users' browsers</em>; they do not stop
 * a user downloading an infected file onto their own machine.
 */
public interface VirusScanner {

    /**
     * Scans {@code content}.
     *
     * @throws StorageException if the content is rejected, using
     *                          {@link StorageException#rejected} so the caller
     *                          sees a 400
     */
    void scan(byte[] content, String fileName);

    /** Whether a real scanner is wired in, for the health and info endpoints. */
    boolean isEnabled();
}
