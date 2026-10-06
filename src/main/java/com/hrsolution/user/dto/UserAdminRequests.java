package com.hrsolution.user.dto;

import com.hrsolution.common.validation.IndianFormats;
import com.hrsolution.common.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** Request bodies for the user-administration endpoints. */
public final class UserAdminRequests {

    private UserAdminRequests() {
    }

    @Schema(description = "Create a staff account. Created ACTIVE and pre-verified, "
            + "because an administrator vouching for it is the verification.")
    public record CreateUser(

            @NotBlank(message = "First name is required")
            @Size(max = 100)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Priya")
            String firstName,

            @Size(max = 100)
            String lastName,

            @NotBlank(message = "Email is required")
            @Email(message = "must be a valid email address")
            @Size(max = 180)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "priya@hrsolution.local")
            String email,

            @Pattern(regexp = IndianFormats.MOBILE, message = IndianFormats.MOBILE_MESSAGE)
            String phone,

            @NotBlank(message = "Password is required")
            @StrongPassword
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Initial password; the user should change it on first sign-in")
            String password,

            @NotEmpty(message = "At least one role is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "[\"HR_RECRUITER\"]")
            Set<String> roles) {
    }

    @Schema(description = "Edit a user's profile fields")
    public record UpdateUser(

            @NotBlank(message = "First name is required")
            @Size(max = 100)
            String firstName,

            @Size(max = 100)
            String lastName,

            @Pattern(regexp = IndianFormats.MOBILE, message = IndianFormats.MOBILE_MESSAGE)
            String phone) {
    }

    @Schema(description = "Replace the set of roles held by a user")
    public record AssignRoles(

            @NotEmpty(message = "At least one role is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Full replacement set of role names",
                    example = "[\"ADMIN\",\"ACCOUNTS\"]")
            Set<String> roles) {
    }

    @Schema(description = "Replace the set of permissions granted by a role")
    public record UpdateRolePermissions(

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Full replacement set of permission names. "
                            + "Send an empty array to strip the role of all permissions.",
                    example = "[\"CLIENT_READ\",\"CLIENT_WRITE\"]")
            Set<String> permissions) {
    }

    @Schema(description = "Disable an account, with a reason recorded in the audit log")
    public record DisableUser(

            @Size(max = 300)
            @Schema(example = "Left the company on 31-10-2026")
            String reason) {
    }
}
