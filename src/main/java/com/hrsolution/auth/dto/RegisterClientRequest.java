package com.hrsolution.auth.dto;

import com.hrsolution.common.validation.IndianFormats;
import com.hrsolution.common.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Client-company self-registration.
 *
 * <p>Creates a {@code PENDING_APPROVAL} user with the {@code CLIENT} role. The
 * account cannot log in until an administrator approves it, because a client
 * login can see deployed workers and invoices and so must be vouched for.
 *
 * <p>Only the contact person and a self-declared company name are collected
 * here. The full client record - GSTIN, billing address, sites, contracts -
 * belongs to Phase 4, and is created from the approval screen once the
 * registration has been checked.
 */
@Schema(description = "Client-company self-registration; stays PENDING_APPROVAL until an admin approves")
public record RegisterClientRequest(

        @NotBlank(message = "Company name is required")
        @Size(max = 200)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Bharat Textiles Private Limited")
        String companyName,

        @NotBlank(message = "Contact person first name is required")
        @Size(max = 100)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Rajesh")
        String firstName,

        @Size(max = 100)
        @Schema(example = "Kulkarni")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "must be a valid email address")
        @Size(max = 180)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "rajesh@bharattextiles.example.com")
        String email,

        @NotBlank(message = "Mobile number is required")
        @Pattern(regexp = IndianFormats.MOBILE, message = IndianFormats.MOBILE_MESSAGE)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "9876500000")
        String phone,

        @NotBlank(message = "Password is required")
        @StrongPassword
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Str0ng@Pass")
        String password,

        @AssertTrue(message = "You must accept the privacy policy to register")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
        boolean consentGiven) {
}
