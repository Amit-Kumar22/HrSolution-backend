package com.hrsolution.user.bootstrap;

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
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Creates one demo account per role so every permission path can be exercised
 * by hand.
 *
 * <p><strong>{@code local} profile only.</strong> The password below is in
 * source control and therefore public. {@code @Profile("local")} is what keeps
 * these accounts out of dev and production - do not widen it, and do not copy
 * this class as a template for environment-agnostic seeding.
 *
 * <p>Idempotent: existing accounts are skipped, so passwords changed while
 * testing survive a restart.
 */
@Slf4j
@Component
@Order(2)
@Profile("local")
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    /** Satisfies the password policy: upper, lower, digit and special. */
    public static final String DEMO_PASSWORD = "Demo@12345";

    private static final String DOMAIN = "@hrsolution.local";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    private record DemoUser(String localPart, String firstName, String lastName,
                            RoleName role, UserStatus status) {
    }

    private static final List<DemoUser> DEMO_USERS = List.of(
            new DemoUser("superadmin", "Vikram", "Shetty", RoleName.SUPER_ADMIN, UserStatus.ACTIVE),
            new DemoUser("admin", "Asha", "Patil", RoleName.ADMIN, UserStatus.ACTIVE),
            new DemoUser("hr", "Priya", "Nair", RoleName.HR_RECRUITER, UserStatus.ACTIVE),
            new DemoUser("ops", "Ramesh", "Yadav", RoleName.OPERATIONS_MANAGER, UserStatus.ACTIVE),
            new DemoUser("supervisor", "Sunil", "Gaikwad", RoleName.SITE_SUPERVISOR, UserStatus.ACTIVE),
            new DemoUser("accounts", "Meena", "Iyer", RoleName.ACCOUNTS, UserStatus.ACTIVE),
            new DemoUser("client", "Rajesh", "Kulkarni", RoleName.CLIENT, UserStatus.ACTIVE),
            new DemoUser("worker", "Sanjay", "Kamble", RoleName.WORKER, UserStatus.ACTIVE),
            new DemoUser("candidate", "Neha", "Joshi", RoleName.CANDIDATE, UserStatus.ACTIVE),

            // Two accounts left deliberately unusable, so the blocked-sign-in
            // paths can be tested without having to construct them by hand.
            new DemoUser("pending.client", "Deepak", "Rao", RoleName.CLIENT,
                    UserStatus.PENDING_APPROVAL),
            new DemoUser("unverified", "Kavita", "Desai", RoleName.CANDIDATE,
                    UserStatus.PENDING_VERIFICATION));

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int created = 0;

        for (DemoUser demo : DEMO_USERS) {
            String email = demo.localPart() + DOMAIN;
            if (userRepository.existsByEmailIgnoringSoftDelete(email)) {
                continue;
            }

            Role role = roleRepository.findWithPermissionsByName(demo.role().name())
                    .orElseThrow(() -> new IllegalStateException(
                            "Role %s is missing; it is seeded by Flyway migration V2."
                                    .formatted(demo.role())));

            User user = new User();
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
            user.setFirstName(demo.firstName());
            user.setLastName(demo.lastName());
            user.setPhone("98765" + String.format("%05d", 10000 + created));
            user.setStatus(demo.status());
            user.setEmailVerified(demo.status() == UserStatus.ACTIVE);
            if (demo.status() == UserStatus.ACTIVE) {
                user.setEmailVerifiedAt(Instant.now());
            }
            if (demo.role() == RoleName.CLIENT) {
                user.setPendingCompanyName("Bharat Textiles Private Limited");
            }
            user.setConsentGivenAt(Instant.now());
            user.setRoles(Set.of(role));

            userRepository.save(user);
            created++;
        }

        if (created > 0) {
            log.warn("""
                    Seeded {} demo account(s) - LOCAL PROFILE ONLY.
                      Password for all of them: {}
                      superadmin{}  admin{}  hr{}  ops{}
                      supervisor{}  accounts{}  client{}  worker{}  candidate{}
                      pending.client{} (PENDING_APPROVAL - sign-in blocked)
                      unverified{} (PENDING_VERIFICATION - sign-in blocked)""",
                    created, DEMO_PASSWORD,
                    DOMAIN, DOMAIN, DOMAIN, DOMAIN, DOMAIN, DOMAIN, DOMAIN, DOMAIN, DOMAIN,
                    DOMAIN, DOMAIN);
        }
    }
}
