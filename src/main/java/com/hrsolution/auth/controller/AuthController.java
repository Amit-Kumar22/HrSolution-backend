package com.hrsolution.auth.controller;

import com.hrsolution.auth.dto.AuthTokenResponse;
import com.hrsolution.auth.dto.CurrentUserResponse;
import com.hrsolution.auth.dto.LoginRequest;
import com.hrsolution.auth.dto.MessageResponse;
import com.hrsolution.auth.dto.PasswordRequests;
import com.hrsolution.auth.dto.RegisterCandidateRequest;
import com.hrsolution.auth.dto.RegisterClientRequest;
import com.hrsolution.auth.dto.RegistrationResponse;
import com.hrsolution.auth.service.AuthService;
import com.hrsolution.auth.service.RefreshCookieService;
import com.hrsolution.auth.service.RefreshTokenService;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.common.web.RequestContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Authentication endpoints.
 *
 * <p><strong>How the two tokens are delivered.</strong> The access token is in
 * the JSON body, for the client to keep <em>in memory</em> and send as
 * {@code Authorization: Bearer ...}. The refresh token is never in the body -
 * it is set as an {@code HttpOnly; Secure; SameSite=Strict} cookie scoped to
 * {@code /api/v1/auth}, so script cannot read it and the browser only attaches
 * it to these endpoints.
 *
 * <p>A client therefore calls {@code /auth/refresh} with no body at all: the
 * browser supplies the cookie automatically. On startup it calls
 * {@code /auth/refresh} once to recover a session, since the access token was
 * lost when the page unloaded.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration, sign-in, token refresh and credential management")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieService refreshCookieService;
    private final RefreshTokenService refreshTokenService;

    // ==================================================================
    // Registration
    // ==================================================================

    @PostMapping("/register/candidate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register as a job seeker",
            description = "Creates a PENDING_VERIFICATION account with the CANDIDATE role and emails "
                    + "a verification link. Sign-in is blocked until the email is confirmed.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Registered; verification email sent"),
            @ApiResponse(responseCode = "400", description = "Validation failed (VALIDATION_FAILED)"),
            @ApiResponse(responseCode = "409", description = "Email already registered (DUPLICATE_RESOURCE)"),
            @ApiResponse(responseCode = "429", description = "Too many registrations from this IP (RATE_LIMIT_EXCEEDED)")
    })
    public RegistrationResponse registerCandidate(@Valid @RequestBody RegisterCandidateRequest request,
                                                  HttpServletRequest httpRequest) {
        return authService.registerCandidate(request, RequestContext.from(httpRequest));
    }

    @PostMapping("/register/client")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register as a client company",
            description = "Creates a PENDING_APPROVAL account with the CLIENT role and notifies an "
                    + "administrator. Sign-in is blocked until an admin approves it, because a client "
                    + "login can see deployed workers and invoices.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Registration received; awaiting approval"),
            @ApiResponse(responseCode = "400", description = "Validation failed (VALIDATION_FAILED)"),
            @ApiResponse(responseCode = "409", description = "Email already registered (DUPLICATE_RESOURCE)")
    })
    public RegistrationResponse registerClient(@Valid @RequestBody RegisterClientRequest request,
                                               HttpServletRequest httpRequest) {
        return authService.registerClient(request, RequestContext.from(httpRequest));
    }

    // ==================================================================
    // Sign in / refresh / sign out
    // ==================================================================

    @PostMapping("/login")
    @Operation(summary = "Sign in",
            description = "Returns an access token in the body and sets the refresh token as an "
                    + "HttpOnly cookie. Five failed attempts lock the account for 15 minutes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Signed in"),
            @ApiResponse(responseCode = "401",
                    description = "Invalid credentials, or the account is locked (UNAUTHENTICATED)"),
            @ApiResponse(responseCode = "403",
                    description = "Credentials correct but the account is not active - unverified, "
                            + "awaiting approval or disabled (ACCESS_DENIED)"),
            @ApiResponse(responseCode = "429", description = "Too many attempts (RATE_LIMIT_EXCEEDED)")
    })
    public AuthTokenResponse login(@Valid @RequestBody LoginRequest request,
                                   HttpServletRequest httpRequest,
                                   HttpServletResponse httpResponse) {
        AuthService.AuthResult result = authService.login(request, RequestContext.from(httpRequest));
        refreshCookieService.set(httpResponse,
                result.refreshToken().plaintext(), result.refreshToken().expiresAt());
        return result.body();
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate tokens",
            description = "Takes no body - the refresh token comes from the HttpOnly cookie. "
                    + "The presented token is consumed and replaced. Replaying an already-used token "
                    + "is treated as theft: the entire token family is revoked and re-authentication "
                    + "is required.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New access token issued and cookie rotated"),
            @ApiResponse(responseCode = "401",
                    description = "Cookie missing, expired, or already used (UNAUTHENTICATED)")
    })
    public AuthTokenResponse refresh(HttpServletRequest httpRequest,
                                     HttpServletResponse httpResponse) {
        String presentedToken = refreshCookieService.read(httpRequest)
                .orElseThrow(() -> new RefreshTokenService.InvalidRefreshTokenException(
                        "No session cookie was sent. Please sign in."));

        AuthService.AuthResult result =
                authService.refresh(presentedToken, RequestContext.from(httpRequest));

        refreshCookieService.set(httpResponse,
                result.refreshToken().plaintext(), result.refreshToken().expiresAt());
        return result.body();
    }

    @PostMapping("/logout")
    @Operation(summary = "Sign out of this device",
            description = "Revokes the refresh token in the cookie and clears it. Idempotent - "
                    + "succeeds even without a valid cookie, since the caller's goal is already met.")
    @ApiResponse(responseCode = "200", description = "Signed out")
    public MessageResponse logout(@AuthenticationPrincipal AuthenticatedUser caller,
                                  HttpServletRequest httpRequest,
                                  HttpServletResponse httpResponse) {
        String presentedToken = refreshCookieService.read(httpRequest).orElse(null);

        // The caller may be anonymous: an expired access token with a live
        // refresh cookie is exactly when a client wants to log out.
        Long userId = Optional.ofNullable(caller).map(AuthenticatedUser::id).orElse(null);
        String email = Optional.ofNullable(caller).map(AuthenticatedUser::email).orElse(null);

        authService.logout(presentedToken, userId, email, RequestContext.from(httpRequest));
        refreshCookieService.clear(httpResponse);

        return MessageResponse.of("Signed out.");
    }

    @PostMapping("/logout-all")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Sign out of every device",
            description = "Revokes all of the user's refresh tokens. Use after a suspected "
                    + "compromise.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "All sessions revoked"),
            @ApiResponse(responseCode = "401", description = "Not signed in (UNAUTHENTICATED)")
    })
    public MessageResponse logoutAll(@AuthenticationPrincipal AuthenticatedUser caller,
                                     HttpServletRequest httpRequest,
                                     HttpServletResponse httpResponse) {
        authService.logoutAll(caller.id(), caller.email(), RequestContext.from(httpRequest));
        refreshCookieService.clear(httpResponse);
        return MessageResponse.of("Signed out of all devices.");
    }

    // ==================================================================
    // Email verification
    // ==================================================================

    @PostMapping("/verify-email")
    @Operation(summary = "Confirm an email address",
            description = "Consumes the single-use token from the verification link and activates "
                    + "the account.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email confirmed; the account is now active"),
            @ApiResponse(responseCode = "400",
                    description = "Token unknown, expired or already used (VALIDATION_FAILED)")
    })
    public MessageResponse verifyEmail(@Valid @RequestBody PasswordRequests.VerifyEmail request,
                                       HttpServletRequest httpRequest) {
        authService.verifyEmail(request.token(), RequestContext.from(httpRequest));
        return MessageResponse.of("Email address confirmed. You can now sign in.");
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Send a new verification link",
            description = "Always reports success, whether or not the email is registered - a "
                    + "different answer would reveal which addresses have accounts.")
    @ApiResponse(responseCode = "200", description = "Acknowledged")
    public MessageResponse resendVerification(
            @Valid @RequestBody PasswordRequests.ResendVerification request,
            HttpServletRequest httpRequest) {
        authService.resendVerification(request.email(), RequestContext.from(httpRequest));
        return MessageResponse.of(
                "If that email is registered and not yet confirmed, a new link has been sent.");
    }

    // ==================================================================
    // Password reset and change
    // ==================================================================

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset",
            description = "Always reports success, whether or not the email is registered. "
                    + "The link is single-use and expires in 30 minutes.")
    @ApiResponse(responseCode = "200", description = "Acknowledged")
    public MessageResponse forgotPassword(
            @Valid @RequestBody PasswordRequests.ForgotPassword request,
            HttpServletRequest httpRequest) {
        authService.forgotPassword(request.email(), RequestContext.from(httpRequest));
        return MessageResponse.of("If that email is registered, a reset link has been sent.");
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Complete a password reset",
            description = "Sets a new password using the emailed token, then revokes every "
                    + "existing session - whoever was signed in may be the reason for the reset.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password updated; all sessions revoked"),
            @ApiResponse(responseCode = "400",
                    description = "Token invalid, or the password breaks policy (VALIDATION_FAILED)")
    })
    public MessageResponse resetPassword(
            @Valid @RequestBody PasswordRequests.ResetPassword request,
            HttpServletRequest httpRequest) {
        authService.resetPassword(request, RequestContext.from(httpRequest));
        return MessageResponse.of("Password updated. Please sign in with your new password.");
    }

    @PostMapping("/change-password")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Change your own password",
            description = "Requires the current password. On success every session is revoked, "
                    + "including this one, so the client must sign in again.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password changed; all sessions revoked"),
            @ApiResponse(responseCode = "400",
                    description = "Current password wrong, or the new one breaks policy (VALIDATION_FAILED)"),
            @ApiResponse(responseCode = "401", description = "Not signed in (UNAUTHENTICATED)")
    })
    public MessageResponse changePassword(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody PasswordRequests.ChangePassword request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        authService.changePassword(caller.id(), request, RequestContext.from(httpRequest));
        refreshCookieService.clear(httpResponse);
        return MessageResponse.of("Password changed. Please sign in again.");
    }

    // ==================================================================
    // Current user
    // ==================================================================

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get the signed-in user",
            description = "Returns the profile with roles and the union of permissions across them. "
                    + "Permissions are for deciding what to display; the server re-derives them from "
                    + "the database on every request.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The signed-in user"),
            @ApiResponse(responseCode = "401", description = "Not signed in (UNAUTHENTICATED)")
    })
    public CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser caller) {
        return authService.currentUser(caller.id());
    }
}
