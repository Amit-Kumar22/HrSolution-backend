package com.hrsolution.support;

import com.hrsolution.user.entity.Role;
import com.hrsolution.user.entity.RoleName;
import com.hrsolution.user.entity.User;
import com.hrsolution.user.entity.UserStatus;
import com.hrsolution.user.repository.RoleRepository;
import com.hrsolution.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for full-stack API tests: real Spring context, real MySQL from
 * Testcontainers, real Flyway migrations, real security filter chain.
 *
 * <p>Extend it and inject {@link #mockMvc}. The Spring context and the MySQL
 * container are cached across every subclass in a run, so adding test classes is
 * cheap - the container only starts once.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    /** Satisfies the password policy. */
    protected static final String TEST_PASSWORD = "Test@Pass123";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Creates an ACTIVE user holding {@code role} and returns a ready-to-use
     * {@code Authorization} header value.
     *
     * <p>The token is obtained by calling the real {@code /auth/login} endpoint
     * rather than by minting one directly. That is deliberate: a hand-built
     * token would skip the decoder, the issuer check and
     * {@code JwtUserAuthenticationConverter} - precisely the code most worth
     * exercising. It also means a break in the login path fails these tests
     * instead of hiding behind a shortcut.
     *
     * @return e.g. {@code "Bearer eyJraWQ..."}
     */
    protected String bearerFor(RoleName role) throws Exception {
        String email = createUser(role);
        return "Bearer " + loginForAccessToken(email, TEST_PASSWORD);
    }

    /** Creates an ACTIVE, pre-verified user with the given role; returns the email. */
    @Transactional
    protected String createUser(RoleName roleName) {
        String email = "it-" + roleName.name().toLowerCase() + "-"
                + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

        Role role = roleRepository.findWithPermissionsByName(roleName.name())
                .orElseThrow(() -> new IllegalStateException(
                        "Role " + roleName + " was not seeded by migration V2"));

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        user.setFirstName("Test");
        user.setLastName(roleName.name());
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(Instant.now());
        user.setConsentGivenAt(Instant.now());
        user.setRoles(Set.of(role));

        userRepository.save(user);
        return email;
    }

    protected String loginForAccessToken(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","rememberMe":false}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Extracted by hand rather than with an ObjectMapper: both Jackson 2 and
        // Jackson 3 are on the test classpath, and picking the wrong one is a
        // subtle trap documented in docs/decisions.md.
        String marker = "\"accessToken\":\"";
        int start = body.indexOf(marker) + marker.length();
        return body.substring(start, body.indexOf('"', start));
    }
}
