package com.hrsolution.common.crypto;

/**
 * A sensitive field could not be encrypted, decrypted or indexed.
 *
 * <p>Unchecked and deliberately opaque: the message never contains the value
 * involved, because these exceptions get logged. Mapped to a 500 by
 * {@code GlobalExceptionHandler} - a decryption failure means the key is wrong
 * or the data was altered, and neither is something a caller can fix by
 * changing their request.
 */
public class FieldCryptoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FieldCryptoException(String message) {
        super(message);
    }

    public FieldCryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
