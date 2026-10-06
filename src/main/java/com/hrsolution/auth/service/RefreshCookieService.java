package com.hrsolution.auth.service;

import com.hrsolution.common.config.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

/**
 * Sets, reads and clears the refresh-token cookie.
 *
 * <p>Every attribute is a deliberate defence:
 * <ul>
 *   <li>{@code HttpOnly} - JavaScript cannot read it, so an XSS flaw cannot
 *       exfiltrate the one credential that grants long-lived access.</li>
 *   <li>{@code Secure} - never sent over plain HTTP. Configurable only so that
 *       plain-HTTP localhost works, where a Secure cookie would simply never be
 *       sent at all.</li>
 *   <li>{@code SameSite=Strict} - not attached to any cross-site request. This
 *       is the primary CSRF defence for {@code /auth/refresh} and
 *       {@code /auth/logout}: a form or fetch from another origin cannot cause
 *       the browser to send it.</li>
 *   <li>{@code Path=/api/v1/auth} - narrow, so the refresh token is not
 *       attached to every ordinary API call. Fewer requests carrying it means
 *       fewer places it can leak into a log or a proxy.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class RefreshCookieService {

    private final AuthProperties authProperties;

    /** Issues the cookie, with a max-age matching the token's own expiry. */
    public void set(HttpServletResponse response, String token, Instant expiresAt) {
        AuthProperties.Refresh settings = authProperties.getRefresh();

        Duration maxAge = Duration.between(Instant.now(), expiresAt);
        if (maxAge.isNegative()) {
            maxAge = Duration.ZERO;
        }

        ResponseCookie cookie = ResponseCookie.from(settings.getCookieName(), token)
                .httpOnly(true)
                .secure(settings.isCookieSecure())
                .sameSite(settings.getCookieSameSite())
                .path(settings.getCookiePath())
                .maxAge(maxAge)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Expires the cookie.
     *
     * <p>Name, path and the security attributes must match the cookie that was
     * set, or the browser treats this as a different cookie and leaves the
     * original in place.
     */
    public void clear(HttpServletResponse response) {
        AuthProperties.Refresh settings = authProperties.getRefresh();

        ResponseCookie cookie = ResponseCookie.from(settings.getCookieName(), "")
                .httpOnly(true)
                .secure(settings.isCookieSecure())
                .sameSite(settings.getCookieSameSite())
                .path(settings.getCookiePath())
                .maxAge(Duration.ZERO)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public Optional<String> read(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        String cookieName = authProperties.getRefresh().getCookieName();
        return Arrays.stream(request.getCookies())
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(jakarta.servlet.http.Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }
}
