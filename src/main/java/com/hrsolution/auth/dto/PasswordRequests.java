package com.hrsolution.auth.dto;

import com.hrsolution.common.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request bodies for the credential-management endpoints, grouped because each
 * is a couple of fields and they are only ever read together.
 */
public final class PasswordRequests {

    private PasswordRequests() {
    }

    @Schema(description = "Start a password reset. The response never reveals whether the email exists.")
    public record ForgotPassword(

            @NotBlank(message = "Email is required")
            @Email(message = "must be a valid email address")
            @Size(max = 180)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "asha.patil@example.com")
            String email) {
    }

    @Schema(description = "Complete a password reset using the token from the email")
    public record ResetPassword(

            @NotBlank(message = "Token is required")
            @Size(max = 100)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "The single-use token from the reset link")
            String token,

            @NotBlank(message = "Password is required")
            @StrongPassword
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "N3w@Password")
            String newPassword) {
    }

    @Schema(description = "Change the password of the signed-in user")
    public record ChangePassword(

            // Required even though the caller is authenticated: it proves the
            // person at the keyboard is the account owner and not someone who
            // found an unlocked laptop, and it is the standard defence against
            // an XSS payload silently changing the password.
            @NotBlank(message = "Current password is required")
            @Size(max = 128)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String currentPassword,

            @NotBlank(message = "New password is required")
            @StrongPassword
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "N3w@Password")
            String newPassword) {
    }

    @Schema(description = "Confirm an email address using the token from the verification link")
    public record VerifyEmail(

            @NotBlank(message = "Token is required")
            @Size(max = 100)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String token) {
    }

    @Schema(description = "Send a fresh verification link")
    public record ResendVerification(

            @NotBlank(message = "Email is required")
            @Email(message = "must be a valid email address")
            @Size(max = 180)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String email) {
    }
}
