package com.hrsolution.common.storage;

/**
 * The result of storing a file.
 *
 * @param storageKey      opaque key, the only way to retrieve the file. Keep it;
 *                        it cannot be reconstructed.
 * @param originalFileName the name as uploaded, for display
 * @param contentType     the type <strong>detected from the content</strong>,
 *                        which may differ from what the client declared
 * @param sizeBytes       actual bytes written
 * @param checksumSha256  hex SHA-256 of the content, for integrity and
 *                        duplicate detection
 */
public record StoredFile(
        String storageKey,
        String originalFileName,
        String contentType,
        long sizeBytes,
        String checksumSha256) {
}
