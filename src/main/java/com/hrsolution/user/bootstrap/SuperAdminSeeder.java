package com.hrsolution.user.bootstrap;

import com.hrsolution.common.config.AuthProperties;
import com.hrsolution.user.entity.Role;
import com.hrsolution.user.entity.RoleName;
import com.hrsolution.user.entity.User;
import com.hrsolution.user.entity.UserStatus;
import com.hrsolution.user.repository.RoleRepository;
import com.hrsolution.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Creates the first SUPER_ADMIN account at startup.
 *
 * <p><strong>Why not Flyway?</strong> The migration that seeds roles and
 * permissions cannot seed this one: a BCrypt hash cannot be computed in SQL, so
 * the alternative would be committing a pre-computed hash of a known password
 * into a migration file - a published credential for every deployment of this
 * code. Instead the password is read from {@code SUPER_ADMIN_PASSWORD} at
 * startup and hashed here.
 *
 * <p>Idempotent and non-destructive: if the account already exists it is left
 * completely alone, so restarting the application never resets a password an
 * administrator has since changed.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class SuperAdminSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AuthProperties.SuperAdmin settings = authProperties.getSuperAdmin();

        String email = settings.getEmail() == null ? "" : settings.getEmail().trim().toLowerCase();
        String password = settings.getPassword();

        if (email.isBlank() || password == null || password.isBlank()) {
            log.info("No SUPER_ADMIN seed configured (app.auth.super-admin.email / .password). "
                    + "Skipping. Set SUPER_ADMIN_EMAIL and SUPER_ADMIN_PASSWORD to create one.");
            return;
        }

        Optional<User> existing = userRepository.findActiveByEmail(email);
        if (existing.isPresent()) {
            log.debug("SUPER_ADMIN {} already exists; leaving it untouched", email);
            return;
        }

        Role superAdminRole = roleRepository.findWithPermissionsByName(RoleName.SUPER_ADMIN.name())
                .orElseThrow(() -> new IllegalStateException(
                        "The SUPER_ADMIN role is missing. It is seeded by Flyway migration V2."));

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFirstName("Super");
        user.setLastName("Admin");
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(Instant.now());
        user.setRoles(Set.of(superAdminRole));

        userRepository.save(user);

        // The password itself is never logged, only the fact of creation.
        log.warn("Created the initial SUPER_ADMIN account '{}' from environment configuration. "
                + "Sign in and change this password now.", email);
    }
}
