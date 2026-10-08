package com.hrsolution.common.storage;

import java.io.InputStream;

/**
 * File storage, abstracted away from where the bytes actually live.
 *
 * <p>{@link LocalStorageService} is the only implementation today. An
 * S3-compatible one drops in behind this interface without touching a caller,
 * which is why nothing above this layer is allowed to know about
 * {@code java.nio.file.Path} or a bucket name.
 *
 * <p>Callers deal only in {@link #store} and the opaque storage key it returns.
 * They never construct a key, never build a path, and never see the original
 * file name used as one - the upload's file name is attacker-controlled and
 * treated as display text only.
 */
public interface StorageService {

    /**
     * Stores the bytes and returns the key needed to retrieve them.
     *
     * @param request what to store, and where in the logical layout
     * @return the stored file's key, size and detected content type
     * @throws StorageException if the file is rejected or cannot be written
     */
    StoredFile store(StoreRequest request);

    /**
     * Opens the stored file for reading. The caller must close the stream.
     *
     * @throws StorageException if the key is unknown
     */
    InputStream load(String storageKey);

    /**
     * Deletes the file. Returns false when the key was already gone, rather
     * than throwing - deleting something twice is not an error worth
     * propagating.
     */
    boolean delete(String storageKey);

    boolean exists(String storageKey);

    /**
     * An upload to be stored.
     *
     * @param category   logical grouping, used to lay out storage, e.g.
     *                   {@code "logos"} or {@code "worker-documents"}. Must be a
     *                   server-chosen constant, never user input.
     * @param fileName   the name the user uploaded. Used to pick an extension
     *                   and for display; never used to build a path.
     * @param contentType the browser-declared type. Advisory only - the real
     *                   type is detected from the content.
     * @param size       declared length in bytes
     * @param content    the bytes. Consumed once.
     */
    record StoreRequest(String category, String fileName, String contentType,
                        long size, InputStream content) {
    }
}
