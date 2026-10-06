package com.hrsolution.auth.service;

import com.hrsolution.audit.entity.AuditAction;
import com.hrsolution.audit.service.AuditService;
import com.hrsolution.auth.dto.AuthTokenResponse;
import com.hrsolution.auth.dto.CurrentUserResponse;
import com.hrsolution.auth.dto.LoginRequest;
import com.hrsolution.auth.dto.PasswordRequests;
import com.hrsolution.auth.dto.RegisterCandidateRequest;
import com.hrsolution.auth.dto.RegisterClientRequest;
import com.hrsolution.auth.dto.RegistrationResponse;
import com.hrsolution.auth.entity.PasswordResetToken;
import com.hrsolution.auth.entity.RevokedReason;
import com.hrsolution.auth.entity.VerificationToken;
import com.hrsolution.auth.exception.AuthExceptions;
import com.hrsolution.auth.repository.PasswordResetTokenRepository;
import com.hrsolution.auth.repository.VerificationTokenRepository;
import com.hrsolution.common.config.AuthProperties;
import com.hrsolution.common.error.DuplicateResourceException;
import com.hrsolution.common.error.FieldValidationException;
import com.hrsolution.common.error.ResourceNotFoundException;
import com.hrsolution.common.ratelimit.RateLimitService;
import com.hrsolution.common.security.SecureTokens;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.notification.service.EmailService;
import com.hrsolution.user.entity.Role;
import com.hrsolution.user.entity.RoleName;
import com.hrsolution.user.entity.User;
import com.hrsolution.user.entity.UserStatus;
import com.hrsolution.user.repository.RoleRepository;
import com.hrsolution.user.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * The authentication use cases: register, sign in, refresh, sign out, verify an
 * email address, and reset or change a password.
 *
 * <p>Token mechanics live in {@link AccessTokenService} and
 * {@link RefreshTokenService}; this class decides <em>whether</em> a caller
 * should get tokens and records what happened.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final AuditService auditService;
    private final EmailService emailService;
    private final RateLimitService rateLimitService;
    private final AuthProperties authProperties;

    /**
     * A real BCrypt hash of a throwaway value, verified against when the email
     * does not exist.
     *
     * <p>Without it, a missing account returns in microseconds while a wrong
     * password costs the ~250ms of a strength-12 BCrypt verification. That
     * difference is measurable over the network and turns the login endpoint
     * into a reliable account-enumeration oracle. Burning the same work on a
     * dummy hash removes the signal.
     */
    private String dummyPasswordHash;

    @PostConstruct
    void initDummyHash() {
        this.dummyPasswordHash = passwordEncoder.encode("not-a-real-password-" + SecureTokens.generate());
    }

    /**
     * Everything the controller needs after a successful authentication: the
     * JSON body, and the refresh token to put in the cookie.
     */
    public record AuthResult(AuthTokenResponse body, RefreshTokenService.IssuedRefreshToken refreshToken) {
    }

    // ==================================================================
    // Sign in
    // ==================================================================

    /**
     * Verifies credentials and issues a token pair.
     *
     * <p>The order of checks is a security decision, not a style one:
     *
     * <ol>
     *   <li>per-email rate limit - before any database work, so a flood is
     *       cheap to reject;</li>
     *   <li>lockout - before the password check, so a locked account cannot be
     *       brute-forced through the lock;</li>
     *   <li><strong>password</strong>;</li>
     *   <li>account status - only <em>after</em> the password is known to be
     *       correct, so "awaiting approval" or "verify your email" is never
     *       revealed to someone who merely guessed an email address.</li>
     * </ol>
     */
    @Transactional
    public AuthResult login(LoginRequest request, RequestContext context) {
        String email = normaliseEmail(request.email());

        // Per-email, in addition to the per-IP limit applied by RateLimitFilter.
        // A shared office NAT means many users behind one IP, so an IP-only
        // limit is both too loose per account and too tight per office.
        rateLimitService.consumeOrThrow("login:email:" + email,
                authProperties.getRateLimit().getLoginPerEmail());

        Optional<User> maybeUser = userRepository.findActiveByEmailWithRoles(email);

        if (maybeUser.isEmpty()) {
            // Same work as a real verification, to keep the timing flat.
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            auditService.recordSecurityEvent(AuditAction.LOGIN_FAILURE, null, email, false,
                    "No account for this email address", context);
            throw new AuthExceptions.AuthenticationFailed();
        }

        User user = maybeUser.get();

        if (user.isLocked()) {
            auditService.recordSecurityEvent(AuditAction.LOGIN_BLOCKED, user.getId(), email, false,
                    "Sign-in attempted while locked until " + user.getLockedUntil(), context);
            throw new AuthExceptions.AccountLocked(user.lockRemainingSeconds());
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            handleFailedPassword(user, context);
            throw new AuthExceptions.AuthenticationFailed();
        }

        if (!user.getStatus().canLogin()) {
            auditService.recordSecurityEvent(AuditAction.LOGIN_BLOCKED, user.getId(), email, false,
                    "Correct password but account status is " + user.getStatus(), context);
            throw new AuthExceptions.AccountNotActive(statusMessage(user.getStatus()));
        }

        // Committed in its own transaction, like the failure path, so the
        // "last signed in" record cannot be lost to a later rollback.
        loginAttemptService.recordSuccess(user.getId(), context.ipAddress());

        auditService.recordSecurityEvent(AuditAction.LOGIN_SUCCESS, user.getId(), email, true,
                "Signed in" + (request.rememberMe() ? " with remember-me" : ""), context);

        return issueTokens(user, request.rememberMe(), context);
    }

    /**
     * Records the failure through {@link LoginAttemptService}, which commits in
     * its own transaction.
     *
     * <p>This method is about to throw, rolling this transaction back. Anything
     * written here on the current entity would be discarded - which is exactly
     * how a lockout ends up counting to zero forever.
     */
    private void handleFailedPassword(User user, RequestContext context) {
        LoginAttemptService.FailureOutcome outcome = loginAttemptService.recordFailure(user.getId());

        auditService.recordSecurityEvent(AuditAction.LOGIN_FAILURE, user.getId(), user.getEmail(),
                false, "Incorrect password (attempt %d of %d)"
                        .formatted(outcome.failedAttempts(), outcome.maxAttempts()), context);

        if (outcome.nowLocked()) {
            auditService.recordSecurityEvent(AuditAction.ACCOUNT_LOCKED, user.getId(), user.getEmail(),
                    false, "Locked after %d failed attempts from ip=%s"
                            .formatted(outcome.failedAttempts(), context.ipAddress()), context);
        }
    }

    private String statusMessage(UserStatus status) {
        return switch (status) {
            case PENDING_VERIFICATION ->
                    "Please confirm your email address before signing in. "
                            + "Use 'resend verification' if you no longer have the link.";
            case PENDING_APPROVAL ->
                    "Your registration is awaiting approval. You will be emailed once it is activated.";
            case DISABLED -> "This account has been disabled. Please contact your administrator.";
            case REJECTED -> "This registration was not approved. Please contact us for details.";
            case ACTIVE -> "This account is not available.";
        };
    }

    private AuthResult issueTokens(User user, boolean rememberMe, RequestContext context) {
        AccessTokenService.MintedAccessToken accessToken = accessTokenService.mint(user);
        RefreshTokenService.IssuedRefreshToken refreshToken =
                refreshTokenService.issueNewFamily(user, rememberMe, context);

        AuthTokenResponse body = AuthTokenResponse.of(
                accessToken.value(),
                accessToken.expiresInSeconds(),
                accessToken.expiresAt(),
                CurrentUserResponse.from(user));

        return new AuthResult(body, refreshToken);
    }

    // ==================================================================
    // Refresh
    // ==================================================================

    /**
     * Rotates the refresh token and issues a fresh access token.
     *
     * <p>Reuse detection lives in {@link RefreshTokenService#rotate}; by the
     * time this returns normally, the presented token has been consumed and
     * cannot be used again.
     */
    @Transactional
    public AuthResult refresh(String presentedToken, RequestContext context) {
        RefreshTokenService.RotationResult rotation =
                refreshTokenService.rotate(presentedToken, context);

        // Reloaded with roles and permissions, which the access token claims
        // need and which the rotation path does not fetch.
        User user = userRepository.findActiveByIdWithRoles(rotation.userId())
                .orElseThrow(() -> new RefreshTokenService.InvalidRefreshTokenException(
                        "This account is no longer available."));

        AccessTokenService.MintedAccessToken accessToken = accessTokenService.mint(user);

        AuthTokenResponse body = AuthTokenResponse.of(
                accessToken.value(),
                accessToken.expiresInSeconds(),
                accessToken.expiresAt(),
                CurrentUserResponse.from(user));

        return new AuthResult(body, rotation.token());
    }

    // ==================================================================
    // Sign out
    // ==================================================================

    /**
     * Revokes the current device's refresh token.
     *
     * <p>Deliberately tolerant: a logout with a missing or already-revoked
     * token still succeeds. The caller's goal is "end my session", and that
     * goal is already met - returning an error would only leave a client stuck
     * on a screen it cannot leave.
     */
    @Transactional
    public void logout(String presentedToken, Long userId, String email, RequestContext context) {
        if (presentedToken != null) {
            refreshTokenService.revokeByToken(presentedToken, RevokedReason.LOGOUT);
        }
        auditService.recordSecurityEvent(AuditAction.LOGOUT, userId, email, true,
                "Signed out of this device", context);
    }

    @Transactional
    public void logoutAll(Long userId, String email, RequestContext context) {
        int revoked = refreshTokenService.revokeAllForUser(userId, RevokedReason.LOGOUT_ALL);
        auditService.recordSecurityEvent(AuditAction.LOGOUT_ALL, userId, email, true,
                "Signed out of all devices (%d session(s) revoked)".formatted(revoked), context);
    }

    // ==================================================================
    // Registration
    // ==================================================================

    @Transactional
    public RegistrationResponse registerCandidate(RegisterCandidateRequest request,
                                                  RequestContext context) {
        String email = normaliseEmail(request.email());
        rejectIfEmailTaken(email);
        rejectPasswordEqualToEmail(request.password(), email);

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(trimToNull(request.lastName()));
        user.setPhone(trimToNull(request.phone()));
        // Must confirm the address before signing in - otherwise anyone could
        // register with someone else's email and start receiving their mail.
        user.setStatus(UserStatus.PENDING_VERIFICATION);
        user.setConsentGivenAt(Instant.now());
        user.setRoles(Set.of(requireRole(RoleName.CANDIDATE)));

        User saved = userRepository.save(user);

        String token = issueVerificationToken(saved);
        emailService.sendVerificationEmail(saved.getEmail(), saved.fullName(), token);

        auditService.recordSecurityEvent(AuditAction.REGISTERED, saved.getId(), email, true,
                "Candidate self-registration", context);

        return new RegistrationResponse(saved.getId(), saved.getEmail(), saved.getStatus(),
                "Registered. Check your inbox for the verification link.");
    }

    @Transactional
    public RegistrationResponse registerClient(RegisterClientRequest request,
                                               RequestContext context) {
        String email = normaliseEmail(request.email());
        rejectIfEmailTaken(email);
        rejectPasswordEqualToEmail(request.password(), email);

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(trimToNull(request.lastName()));
        user.setPhone(trimToNull(request.phone()));
        user.setPendingCompanyName(request.companyName().trim());
        // A client login can see deployed workers and invoices, so it is
        // approved by a human rather than by clicking a link in an email.
        user.setStatus(UserStatus.PENDING_APPROVAL);
        user.setConsentGivenAt(Instant.now());
        user.setRoles(Set.of(requireRole(RoleName.CLIENT)));

        User saved = userRepository.save(user);

        emailService.sendClientRegistrationReceivedEmail(
                saved.getEmail(), saved.fullName(), saved.getPendingCompanyName());
        emailService.sendAdminClientRegistrationNotification(
                saved.getPendingCompanyName(), saved.fullName(), saved.getEmail(), saved.getPhone());

        auditService.recordSecurityEvent(AuditAction.REGISTERED, saved.getId(), email, true,
                "Client self-registration for '%s'".formatted(saved.getPendingCompanyName()), context);

        return new RegistrationResponse(saved.getId(), saved.getEmail(), saved.getStatus(),
                "Registration received. We will email you once your account is approved.");
    }

    // ==================================================================
    // Email verification
    // ==================================================================

    @Transactional
    public void verifyEmail(String presentedToken, RequestContext context) {
        VerificationToken token = verificationTokenRepository
                .findByTokenHash(SecureTokens.hash(presentedToken))
                .orElseThrow(() -> new AuthExceptions.InvalidToken(
                        "This verification link is not valid. Request a new one."));

        if (!token.isUsable()) {
            throw new AuthExceptions.InvalidToken(
                    "This verification link has expired or was already used. Request a new one.");
        }

        token.markUsed();
        User user = token.getUser();
        user.markEmailVerified();

        auditService.recordSecurityEvent(AuditAction.EMAIL_VERIFIED, user.getId(), user.getEmail(),
                true, "Email address confirmed", context);
    }

    /**
     * Issues a fresh verification link.
     *
     * <p>Returns silently for an unknown email, an already-verified account, or
     * one past its hourly cap - the response must look identical in every case,
     * or the endpoint becomes an account-enumeration oracle.
     */
    @Transactional
    public void resendVerification(String rawEmail, RequestContext context) {
        String email = normaliseEmail(rawEmail);
        Optional<User> maybeUser = userRepository.findActiveByEmail(email);

        if (maybeUser.isEmpty()) {
            log.debug("Verification resend requested for unknown email");
            return;
        }
        User user = maybeUser.get();
        if (user.isEmailVerified()) {
            log.debug("Verification resend requested for already-verified user {}", user.getId());
            return;
        }

        int maxPerHour = authProperties.getEmailVerification().getMaxPerHour();
        long recent = verificationTokenRepository.countRecentForUser(
                user.getId(), Instant.now().minus(Duration.ofHours(1)));
        if (recent >= maxPerHour) {
            log.warn("Verification resend cap reached for user {} ({} in the last hour)",
                    user.getId(), recent);
            return;
        }

        String token = issueVerificationToken(user);
        emailService.sendVerificationEmail(user.getEmail(), user.fullName(), token);

        auditService.recordSecurityEvent(AuditAction.VERIFICATION_RESENT, user.getId(),
                user.getEmail(), true, "Verification email resent", context);
    }

    private String issueVerificationToken(User user) {
        // Kill any outstanding tokens first, so a user with several emails in
        // their inbox finds that only the newest link works.
        verificationTokenRepository.invalidateOutstandingForUser(user.getId(), Instant.now());

        String plaintext = SecureTokens.generate();

        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setTokenHash(SecureTokens.hash(plaintext));
        token.setExpiresAt(Instant.now().plus(authProperties.getEmailVerification().getTtl()));
        verificationTokenRepository.save(token);

        return plaintext;
    }

    // ==================================================================
    // Password reset and change
    // ==================================================================

    /**
     * Starts a password reset.
     *
     * <p>Always appears to succeed. An unknown address, an inactive account and
     * a rate-limited one all produce the same response, because any difference
     * would let an attacker test whether an address is registered.
     */
    @Transactional
    public void forgotPassword(String rawEmail, RequestContext context) {
        String email = normaliseEmail(rawEmail);
        Optional<User> maybeUser = userRepository.findActiveByEmail(email);

        if (maybeUser.isEmpty()) {
            log.debug("Password reset requested for an email with no account");
            return;
        }
        User user = maybeUser.get();

        int maxPerHour = authProperties.getPasswordReset().getMaxPerHour();
        long recent = passwordResetTokenRepository.countRecentForUser(
                user.getId(), Instant.now().minus(Duration.ofHours(1)));
        if (recent >= maxPerHour) {
            log.warn("Password reset cap reached for user {} ({} in the last hour)",
                    user.getId(), recent);
            return;
        }

        passwordResetTokenRepository.invalidateOutstandingForUser(user.getId(), Instant.now());

        String plaintext = SecureTokens.generate();
        Duration ttl = authProperties.getPasswordReset().getTtl();

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(SecureTokens.hash(plaintext));
        token.setExpiresAt(Instant.now().plus(ttl));
        token.setIpAddress(context.ipAddress());
        passwordResetTokenRepository.save(token);

        emailService.sendPasswordResetEmail(
                user.getEmail(), user.fullName(), plaintext, ttl.toMinutes());

        auditService.recordSecurityEvent(AuditAction.PASSWORD_RESET_REQUESTED, user.getId(),
                user.getEmail(), true, "Password reset link issued", context);
    }

    @Transactional
    public void resetPassword(PasswordRequests.ResetPassword request, RequestContext context) {
        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHash(SecureTokens.hash(request.token()))
                .orElseThrow(() -> new AuthExceptions.InvalidToken(
                        "This reset link is not valid. Request a new one."));

        if (!token.isUsable()) {
            throw new AuthExceptions.InvalidToken(
                    "This reset link has expired or was already used. Request a new one.");
        }

        User user = token.getUser();
        rejectPasswordEqualToEmail(request.newPassword(), user.getEmail());

        token.markUsed();
        // changePassword also bumps tokenVersion, invalidating live access tokens.
        user.changePassword(passwordEncoder.encode(request.newPassword()));

        // Whoever was signed in may be the attacker who prompted the reset.
        refreshTokenService.revokeAllForUser(user.getId(), RevokedReason.PASSWORD_CHANGED);

        emailService.sendPasswordChangedEmail(user.getEmail(), user.fullName());

        auditService.recordSecurityEvent(AuditAction.PASSWORD_RESET_COMPLETED, user.getId(),
                user.getEmail(), true, "Password reset completed; all sessions revoked", context);
    }

    @Transactional
    public void changePassword(Long userId, PasswordRequests.ChangePassword request,
                               RequestContext context) {
        User user = userRepository.findActiveByIdWithRoles(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            auditService.recordSecurityEvent(AuditAction.PASSWORD_CHANGED, user.getId(),
                    user.getEmail(), false, "Change rejected: current password incorrect", context);
            throw FieldValidationException.of("currentPassword", "is incorrect");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw FieldValidationException.of("newPassword",
                    "must be different from your current password");
        }

        rejectPasswordEqualToEmail(request.newPassword(), user.getEmail());

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        refreshTokenService.revokeAllForUser(user.getId(), RevokedReason.PASSWORD_CHANGED);

        emailService.sendPasswordChangedEmail(user.getEmail(), user.fullName());

        auditService.recordSecurityEvent(AuditAction.PASSWORD_CHANGED, user.getId(),
                user.getEmail(), true, "Password changed; all sessions revoked", context);
    }

    // ==================================================================
    // Current user
    // ==================================================================

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(Long userId) {
        return userRepository.findActiveByIdWithRoles(userId)
                .map(CurrentUserResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** Lower-cased and trimmed, so {@code A@B.com} and {@code a@b.com} are one account. */
    private String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private void rejectIfEmailTaken(String email) {
        if (userRepository.existsByEmailIgnoringSoftDelete(email)) {
            throw DuplicateResourceException.of("An account", "email", email);
        }
    }

    /**
     * Rejects a password that is the email address, or contains its local part.
     *
     * <p>Required by the password policy and not expressible as a single-field
     * constraint, so it is reported as a field error on {@code password} to
     * match the shape of every other validation failure.
     */
    private void rejectPasswordEqualToEmail(String password, String email) {
        if (password == null || email == null) {
            return;
        }
        String lowerPassword = password.toLowerCase();
        String localPart = email.substring(0, email.indexOf('@') < 0 ? email.length() : email.indexOf('@'));

        if (lowerPassword.equals(email.toLowerCase())
                || (localPart.length() >= 4 && lowerPassword.contains(localPart.toLowerCase()))) {
            throw FieldValidationException.of("password",
                    "must not contain your email address");
        }
    }

    private Role requireRole(RoleName roleName) {
        return roleRepository.findWithPermissionsByName(roleName.name())
                // The nine roles are seeded by migration V2, so a miss means
                // the database was not migrated - a deployment fault, not a
                // bad request.
                .orElseThrow(() -> new IllegalStateException(
                        "Role %s is missing. It is seeded by Flyway migration V2."
                                .formatted(roleName)));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
