package com.hrsolution.auth.service;

import com.hrsolution.common.config.AuthProperties;
import com.hrsolution.common.security.JwtUserAuthenticationConverter;
import com.hrsolution.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Mints signed RS256 access tokens.
 *
 * <p>Claims, and why each is present:
 * <ul>
 *   <li>{@code sub} - the user id. An id rather than the email, so that
 *       changing an email address does not invalidate live tokens.</li>
 *   <li>{@code email}, {@code roles}, {@code permissions} - convenience for the
 *       client, letting it render the right menu without an extra call.
 *       <strong>Not trusted for authorisation</strong>: the server rebuilds
 *       authorities from the database on every request, so a permission revoked
 *       a moment ago takes effect immediately rather than when the token
 *       expires.</li>
 *   <li>{@code tokenVersion} - compared against the user's current value on
 *       every request. This is what lets a password change or an account
 *       disable invalidate outstanding tokens without maintaining a
 *       blacklist.</li>
 *   <li>{@code jti} - a unique id per token, so a specific token can be named
 *       in logs when investigating.</li>
 *   <li>{@code iss} - pinned and verified, so a token from another system that
 *       somehow shares key material is still rejected.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AccessTokenService {

    private final JwtEncoder jwtEncoder;
    private final AuthProperties authProperties;

    /**
     * @param value            the compact serialised JWT
     * @param expiresAt        absolute expiry
     * @param expiresInSeconds relative expiry, for the {@code expires_in} field
     */
    public record MintedAccessToken(String value, Instant expiresAt, long expiresInSeconds) {
    }

    /**
     * Mints a token for {@code user}.
     *
     * <p>Requires {@code roles} and their {@code permissions} to be initialised -
     * load the user via {@code UserRepository.findActiveBy...WithRoles}, or the
     * claims silently come back empty.
     */
    public MintedAccessToken mint(User user) {
        AuthProperties.Jwt settings = authProperties.getJwt();

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(settings.getAccessTokenTtl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(settings.getIssuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim(JwtUserAuthenticationConverter.CLAIM_EMAIL, user.getEmail())
                // Sets, not Lists, would serialise unpredictably; copy to an
                // ordered List so the claim is stable and comparable in tests.
                .claim(JwtUserAuthenticationConverter.CLAIM_ROLES, List.copyOf(user.roleNames()))
                .claim(JwtUserAuthenticationConverter.CLAIM_PERMISSIONS,
                        List.copyOf(user.permissionNames()))
                .claim(JwtUserAuthenticationConverter.CLAIM_TOKEN_VERSION, user.getTokenVersion())
                .build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return new MintedAccessToken(tokenValue, expiresAt, settings.getAccessTokenTtl().toSeconds());
    }
}
