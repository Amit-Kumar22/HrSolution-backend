package com.hrsolution.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Credentials for POST /auth/login")
public record LoginRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "must be a valid email address")
        @Size(max = 180)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "admin@hrsolution.local")
        String email,

        // Deliberately NOT annotated with @StrongPassword. The policy applies
        // when a password is set, not when one is presented: rejecting a login
        // because the stored password predates the current policy would lock
        // the user out of the very screen they would change it from. It would
        // also leak which passwords could possibly be valid.
        @NotBlank(message = "Password is required")
        @Size(max = 128)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "ChangeMe@123")
        String password,

        @Schema(description = "Extends the refresh token lifetime from 7 to 30 days",
                defaultValue = "false")
        boolean rememberMe) {
}
