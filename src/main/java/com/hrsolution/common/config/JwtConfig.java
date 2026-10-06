package com.hrsolution.common.config;

import com.hrsolution.common.security.RsaKeyPair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.List;

/**
 * RS256 signing and verification for access tokens.
 *
 * <p>RS256 rather than HS256: verification needs only the public key, so if
 * this system is later split into several services, or the tokens need
 * verifying by something that must not be able to <em>mint</em> them, nothing
 * has to change. A shared HMAC secret gives every holder the power to issue
 * tokens.
 *
 * <p>Generate a production key pair with:
 * <pre>{@code
 * mkdir -p keys
 * openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out keys/jwt-private.pem
 * openssl rsa -pubout -in keys/jwt-private.pem -out keys/jwt-public.pem
 * }</pre>
 * then point {@code app.auth.jwt.private-key-location} and
 * {@code public-key-location} at them. {@code keys/} and {@code *.pem} are
 * gitignored.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class JwtConfig {

    /** Profiles permitted to run on a throwaway, generated key pair. */
    private static final List<String> EPHEMERAL_KEY_PROFILES = List.of("local", "test");

    private static final int KEY_SIZE_BITS = 2048;

    private final AuthProperties authProperties;
    private final Environment environment;

    @Bean
    public RsaKeyPair rsaKeyPair() {
        Resource privateKeyLocation = authProperties.getJwt().getPrivateKeyLocation();
        Resource publicKeyLocation = authProperties.getJwt().getPublicKeyLocation();

        if (privateKeyLocation != null && publicKeyLocation != null) {
            return loadFromPem(privateKeyLocation, publicKeyLocation);
        }
        // Half-configured is almost certainly a mistake, and silently falling
        // back to a generated pair would hide it.
        if (privateKeyLocation != null || publicKeyLocation != null) {
            throw new IllegalStateException(
                    "Only one of app.auth.jwt.private-key-location / public-key-location is set. "
                            + "Configure both, or neither to use a generated development key pair.");
        }
        return generateEphemeral();
    }

    private RsaKeyPair loadFromPem(Resource privateKeyLocation, Resource publicKeyLocation) {
        try (InputStream privateKeyStream = privateKeyLocation.getInputStream();
             InputStream publicKeyStream = publicKeyLocation.getInputStream()) {

            RSAPrivateKey privateKey = RsaKeyConverters.pkcs8().convert(privateKeyStream);
            RSAPublicKey publicKey = RsaKeyConverters.x509().convert(publicKeyStream);

            log.info("JWT signing keys loaded from {}", privateKeyLocation.getDescription());
            return new RsaKeyPair(publicKey, privateKey);

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read the JWT key pair from %s and %s"
                            .formatted(privateKeyLocation.getDescription(),
                                    publicKeyLocation.getDescription()), e);
        }
    }

    /**
     * Generates a key pair in memory, for development only.
     *
     * <p>Refusing to do this outside local and test is the point: an
     * environment that boots with a generated key invalidates every token on
     * restart, and each instance of a scaled-out deployment would sign with a
     * different key, so tokens would verify only on the instance that issued
     * them. Failing loudly at startup beats debugging that later.
     */
    private RsaKeyPair generateEphemeral() {
        String[] activeProfiles = environment.getActiveProfiles();
        boolean ephemeralAllowed = Arrays.stream(activeProfiles)
                .anyMatch(EPHEMERAL_KEY_PROFILES::contains);

        if (!ephemeralAllowed) {
            throw new IllegalStateException("""
                    No JWT signing keys configured. Set app.auth.jwt.private-key-location and \
                    app.auth.jwt.public-key-location (env JWT_PRIVATE_KEY_LOCATION / \
                    JWT_PUBLIC_KEY_LOCATION). Generate a pair with:
                      openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out keys/jwt-private.pem
                      openssl rsa -pubout -in keys/jwt-private.pem -out keys/jwt-public.pem
                    Active profiles: %s""".formatted(Arrays.toString(activeProfiles)));
        }

        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(KEY_SIZE_BITS);
            KeyPair keyPair = generator.generateKeyPair();

            log.warn("No JWT keys configured - generated an ephemeral {}-bit RSA key pair. "
                            + "Every access token becomes invalid when this process restarts. "
                            + "Acceptable for development only.",
                    KEY_SIZE_BITS);

            return new RsaKeyPair(
                    (RSAPublicKey) keyPair.getPublic(),
                    (RSAPrivateKey) keyPair.getPrivate());

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA key generation is unavailable on this JVM", e);
        }
    }

    @Bean
    public JwtEncoder jwtEncoder(RsaKeyPair rsaKeyPair) {
        return NimbusJwtEncoder
                .withKeyPair(rsaKeyPair.publicKey(), rsaKeyPair.privateKey())
                .algorithm(SignatureAlgorithm.RS256)
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(RsaKeyPair rsaKeyPair) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey(rsaKeyPair.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();

        // Pinning the issuer means a token minted by some other system that
        // happens to share our key material is still rejected.
        OAuth2TokenValidator<Jwt> issuerValidator =
                JwtValidators.createDefaultWithIssuer(authProperties.getJwt().getIssuer());

        // Replaces the default zero-tolerance timestamp check, so a small clock
        // difference between hosts does not reject freshly minted tokens.
        OAuth2TokenValidator<Jwt> timestampValidator =
                new JwtTimestampValidator(authProperties.getJwt().getClockSkew());

        decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                issuerValidator, timestampValidator));

        return decoder;
    }
}
