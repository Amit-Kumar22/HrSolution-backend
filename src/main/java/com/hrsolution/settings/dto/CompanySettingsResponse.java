package com.hrsolution.settings.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * The company profile as returned to authenticated portal users.
 *
 * <p>Includes the bank and statutory registration details, which are needed to
 * render invoices and statutory returns. Phase 3 adds a narrower public variant
 * for the marketing site that omits them.
 */
@Schema(description = "Company profile used across the portal, emails, payslips and invoices")
public record CompanySettingsResponse(

        @Schema(example = "Shree Manpower Services Private Limited")
        String legalName,
        @Schema(example = "Shree Manpower")
        String tradeName,
        @Schema(example = "Reliable contract staffing across India")
        String tagline,
        String about,

        String addressLine1,
        String addressLine2,
        @Schema(example = "Pune")
        String city,
        @Schema(example = "Maharashtra")
        String state,
        @Schema(description = "Two-digit GST state code", example = "27")
        String stateCode,
        @Schema(example = "411001")
        String pincode,
        @Schema(example = "India")
        String country,

        @Schema(example = "+91 20 1234 5678")
        String phone,
        String alternatePhone,
        @Schema(example = "info@example.com")
        String email,
        String supportEmail,
        String website,

        @Schema(example = "27AABCS1234A1Z5")
        String gstin,
        @Schema(example = "AABCS1234A")
        String pan,
        String tan,
        String cin,
        String pfEstablishmentCode,
        String esiEstablishmentCode,
        String ptRegistrationNumber,

        String bankName,
        String bankBranch,
        String bankAccountNumber,
        @Schema(example = "HDFC0001234")
        String bankIfsc,

        String logoPath,

        String linkedinUrl,
        String facebookUrl,
        String twitterUrl,
        String instagramUrl,

        @Schema(description = "When the profile was last edited")
        Instant updatedAt,
        @Schema(description = "Who last edited it")
        String updatedBy) {
}
