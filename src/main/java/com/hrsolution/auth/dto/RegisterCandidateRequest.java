package com.hrsolution.auth.dto;

import com.hrsolution.common.validation.IndianFormats;
import com.hrsolution.common.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Job-seeker self-registration")
public record RegisterCandidateRequest(

        @NotBlank(message = "First name is required")
        @Size(max = 100)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Asha")
        String firstName,

        @Size(max = 100)
        @Schema(example = "Patil")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "must be a valid email address")
        @Size(max = 180)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "asha.patil@example.com")
        String email,

        @NotBlank(message = "Mobile number is required")
        @Pattern(regexp = IndianFormats.MOBILE, message = IndianFormats.MOBILE_MESSAGE)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "9876543210")
        String phone,

        @NotBlank(message = "Password is required")
        @StrongPassword
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Str0ng@Pass")
        String password,

        /*
         * Explicit, unticked-by-default consent, per the DPDP Act. A boolean
         * that must be true is the cleanest way to make the client send it
         * deliberately; the timestamp of the acceptance is stored on the user.
         */
        @AssertTrue(message = "You must accept the privacy policy to register")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Consent to processing personal data, per the DPDP Act",
                example = "true")
        boolean consentGiven) {
}
