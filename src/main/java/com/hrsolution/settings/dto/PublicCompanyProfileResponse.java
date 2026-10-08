package com.hrsolution.settings.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The company profile as served to the <strong>public, unauthenticated</strong>
 * marketing site.
 *
 * <p>Deliberately narrower than {@link CompanySettingsResponse}. Compare the
 * two: this one omits the bank account and IFSC, the TAN, and the PF, ESI and
 * PT registration codes. Those belong on invoices and statutory returns, not on
 * an endpoint anyone on the internet can call - a published bank account is an
 * invitation to invoice fraud, and the registration codes are useful only to
 * someone impersonating the company.
 *
 * <p>GSTIN, PAN and CIN <em>are</em> included: Indian companies are required to
 * display these publicly, and they routinely appear in a website footer.
 *
 * <p>A separate record rather than a filtered view of the full one, so adding a
 * field to the admin DTO cannot accidentally publish it.
 */
@Schema(description = "Company details for the public website header, footer and contact page")
public record PublicCompanyProfileResponse(

        @Schema(example = "Shree Manpower Services Private Limited")
        String legalName,
        @Schema(example = "Shree Manpower")
        String tradeName,
        String tagline,
        String about,

        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String pincode,
        String country,

        String phone,
        String alternatePhone,
        String email,
        String supportEmail,
        String website,

        @Schema(description = "Publicly displayable, as Indian companies are required to show it",
                example = "27AABCS1234A1Z5")
        String gstin,
        String pan,
        String cin,

        @Schema(description = "Public URL of the company logo")
        String logoUrl,

        String linkedinUrl,
        String facebookUrl,
        String twitterUrl,
        String instagramUrl) {
}
