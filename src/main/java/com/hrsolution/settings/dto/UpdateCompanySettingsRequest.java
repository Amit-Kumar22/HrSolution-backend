package com.hrsolution.settings.dto;

import com.hrsolution.common.validation.IndianFormats;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Full replacement of the company profile.
 *
 * <p>A {@code PUT} with complete semantics: every editable field is sent, and
 * omitted fields are cleared. The logo is not here - it is uploaded through its
 * own multipart endpoint in Phase 3, so that saving a phone number cannot
 * accidentally blank the logo.
 */
@Schema(description = "Company profile fields that a SUPER_ADMIN can edit")
public record UpdateCompanySettingsRequest(

        @NotBlank(message = "Legal name is required")
        @Size(max = 200)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Shree Manpower Services Private Limited")
        String legalName,

        @Size(max = 200)
        String tradeName,

        @Size(max = 300)
        String tagline,

        @Size(max = 2000)
        String about,

        @Size(max = 200)
        String addressLine1,

        @Size(max = 200)
        String addressLine2,

        @Size(max = 100)
        String city,

        @Size(max = 100)
        String state,

        @Pattern(regexp = IndianFormats.GST_STATE_CODE, message = IndianFormats.GST_STATE_CODE_MESSAGE)
        @Schema(description = "Two-digit GST state code; decides CGST+SGST vs IGST on invoices", example = "27")
        String stateCode,

        @Pattern(regexp = IndianFormats.PINCODE, message = IndianFormats.PINCODE_MESSAGE)
        String pincode,

        @Size(max = 100)
        String country,

        @Pattern(regexp = IndianFormats.PHONE, message = IndianFormats.PHONE_MESSAGE)
        String phone,

        @Pattern(regexp = IndianFormats.PHONE, message = IndianFormats.PHONE_MESSAGE)
        String alternatePhone,

        @Email(message = "must be a valid email address")
        @Size(max = 180)
        String email,

        @Email(message = "must be a valid email address")
        @Size(max = 180)
        String supportEmail,

        @Size(max = 200)
        String website,

        @Pattern(regexp = IndianFormats.GSTIN, message = IndianFormats.GSTIN_MESSAGE)
        String gstin,

        @Pattern(regexp = IndianFormats.PAN, message = IndianFormats.PAN_MESSAGE)
        String pan,

        @Pattern(regexp = IndianFormats.TAN, message = IndianFormats.TAN_MESSAGE)
        String tan,

        @Pattern(regexp = IndianFormats.CIN, message = IndianFormats.CIN_MESSAGE)
        String cin,

        @Size(max = 30)
        String pfEstablishmentCode,

        @Size(max = 30)
        String esiEstablishmentCode,

        @Size(max = 30)
        String ptRegistrationNumber,

        @Size(max = 120)
        String bankName,

        @Size(max = 120)
        String bankBranch,

        @Pattern(regexp = IndianFormats.BANK_ACCOUNT, message = IndianFormats.BANK_ACCOUNT_MESSAGE)
        String bankAccountNumber,

        @Pattern(regexp = IndianFormats.IFSC, message = IndianFormats.IFSC_MESSAGE)
        String bankIfsc,

        @Size(max = 300)
        String linkedinUrl,

        @Size(max = 300)
        String facebookUrl,

        @Size(max = 300)
        String twitterUrl,

        @Size(max = 300)
        String instagramUrl) {
}
