package com.hrsolution.common.storage;

import com.hrsolution.common.error.ApiException;
import com.hrsolution.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * A file could not be stored or retrieved.
 *
 * <p>Two flavours, because they mean different things to a caller:
 * {@link #rejected} is the user's fault (wrong type, too large) and maps to
 * 400; {@link #failed} is the server's fault (disk full, path unwritable) and
 * maps to 500 with nothing specific in the body.
 */
public class StorageException extends ApiException {

    private static final long serialVersionUID = 1L;

    private StorageException(ErrorCode errorCode, HttpStatus status, String message) {
        super(errorCode, status, message);
    }

    private StorageException(ErrorCode errorCode, HttpStatus status, String message, Throwable cause) {
        super(errorCode, status, message, cause);
    }

    /** The upload itself is unacceptable. The message is safe to show the user. */
    public static StorageException rejected(String message) {
        return new StorageException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, message);
    }

    /**
     * Storage is broken. The message goes to the log; the caller gets a generic
     * 500, because a filesystem path or bucket name is not theirs to see.
     */
    public static StorageException failed(String message, Throwable cause) {
        return new StorageException(
                ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, message, cause);
    }

    public static StorageException notFound(String storageKey) {
        return new StorageException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND,
                "The stored file '%s' no longer exists.".formatted(storageKey));
    }
}
