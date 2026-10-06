package com.hrsolution.common.config;

import com.hrsolution.common.security.DelegatingAccessDeniedHandler;
import com.hrsolution.common.security.DelegatingAuthenticationEntryPoint;
import com.hrsolution.common.security.JwtUserAuthenticationConverter;
import com.hrsolution.common.web.ApiPaths;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * HTTP security: who may reach what, and how a caller is identified.
 *
 * <p>Authentication is a bearer access token, decoded and verified by the
 * resource-server support configured in {@link JwtConfig} and then resolved to
 * a user by {@link JwtUserAuthenticationConverter}.
 *
 * <p>Authorisation is intentionally almost absent from this class.
 * {@code anyRequest().authenticated()} only establishes *that* a caller is
 * signed in; *what* they may do is decided by
 * {@code @PreAuthorize("hasAuthority('...')")} on controller methods, with
 * ownership checks in the services. Keeping the rules next to the endpoints
 * they guard means adding an endpoint cannot accidentally leave it unprotected
 * by a URL pattern nobody remembered to update.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AppProperties appProperties;
    private final JwtUserAuthenticationConverter jwtUserAuthenticationConverter;
    private final DelegatingAuthenticationEntryPoint authenticationEntryPoint;
    private final DelegatingAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // No server-side session and no cookie-based authentication, so
                // Spring's CSRF token has nothing to protect for the API. The
                // one cookie that exists - the refresh token - is defended by
                // SameSite=Strict plus the narrow /api/v1/auth cookie path, and
                // the refresh endpoint additionally requires a JSON content
                // type, which a cross-site HTML form cannot produce.
                // Reasoning recorded in docs/decisions.md.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000L))
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // 'unsafe-inline' is present only because Swagger UI,
                        // served by this application, uses inline script and
                        // style. The API's own responses are JSON.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                        + "script-src 'self' 'unsafe-inline'; "
                                        + "style-src 'self' 'unsafe-inline'; "
                                        + "img-src 'self' data:; "
                                        + "font-src 'self' data:; "
                                        + "frame-ancestors 'none'; "
                                        + "base-uri 'self'; "
                                        + "form-action 'self'")))

                .authorizeHttpRequests(authorize -> authorize
                        // ---- Unauthenticated by necessity ----
                        // Registration, login, token refresh and account
                        // recovery: a caller cannot hold a token yet, or has
                        // lost the ability to obtain one. Each is rate limited
                        // by RateLimitFilter.
                        .requestMatchers(
                                ApiPaths.V1 + "/auth/login",
                                ApiPaths.V1 + "/auth/refresh",
                                ApiPaths.V1 + "/auth/logout",
                                ApiPaths.V1 + "/auth/register/candidate",
                                ApiPaths.V1 + "/auth/register/client",
                                ApiPaths.V1 + "/auth/verify-email",
                                ApiPaths.V1 + "/auth/resend-verification",
                                ApiPaths.V1 + "/auth/forgot-password",
                                ApiPaths.V1 + "/auth/reset-password")
                        .permitAll()

                        // ---- Public website feeds and form submissions ----
                        .requestMatchers(ApiPaths.PUBLIC_V1 + "/**").permitAll()

                        // ---- Infrastructure ----
                        // Health and info only; the remaining actuator
                        // endpoints fall through to authenticated() below.
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                        .permitAll()

                        // ---- Everything else needs a valid access token ----
                        // Fine-grained permissions are enforced per method by
                        // @PreAuthorize, not here.
                        .anyRequest().authenticated())

                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtUserAuthenticationConverter))
                        // Without these, a 401 or 403 from the filter chain
                        // would return an empty body instead of a ProblemDetail.
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))

                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));

        return http.build();
    }

    /**
     * CORS driven entirely by {@code app.cors.allowed-origins}, empty by
     * default so a misconfigured deployment fails closed. Wildcards are not
     * supported because credentials are allowed, and browsers reject that
     * combination anyway.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        AppProperties.Cors settings = appProperties.getCors();

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(settings.getAllowedOrigins());
        configuration.setAllowedMethods(settings.getAllowedMethods());
        configuration.setAllowedHeaders(settings.getAllowedHeaders());
        configuration.setExposedHeaders(settings.getExposedHeaders());
        configuration.setAllowCredentials(settings.isAllowCredentials());
        configuration.setMaxAge(settings.getMaxAgeSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * BCrypt at strength 12, as required by the security conventions. Strength
     * 12 costs roughly a quarter-second per hash, which is the point - it is
     * what makes an offline attack on a leaked table expensive. Do not lower it
     * to speed up tests; tests should shrink the work factor through their own
     * bean instead.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
