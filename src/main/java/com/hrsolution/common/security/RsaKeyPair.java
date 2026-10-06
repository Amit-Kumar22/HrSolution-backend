package com.hrsolution.common.security;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * The RSA key pair used to sign and verify access tokens.
 *
 * <p>Held as a bean so the encoder and decoder are guaranteed to be built from
 * the same pair - a mismatch would produce tokens that this very application
 * cannot verify.
 *
 * @param publicKey  verifies signatures; safe to publish
 * @param privateKey signs tokens; must never leave the server
 */
public record RsaKeyPair(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
}
