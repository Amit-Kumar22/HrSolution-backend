package com.hrsolution.common.domain;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Supplies the value written into {@code created_by} / {@code updated_by}.
 *
 * <p>Resolves to the authenticated principal's name (the user's email, once
 * Phase 2 wires in JWT authentication). Background jobs, Flyway seed data and
 * public endpoints have no principal, so they are recorded as {@value #SYSTEM}
 * rather than left null - that keeps the audit columns meaningful everywhere.
 */
@Component
public class AuditorAwareImpl implements AuditorAware<String> {

    public static final String SYSTEM = "system";

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.of(SYSTEM);
        }
        return Optional.of(authentication.getName());
    }
}
