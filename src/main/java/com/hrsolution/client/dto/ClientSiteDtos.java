package com.hrsolution.client.dto;

import com.hrsolution.common.validation.IndianFormats;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response shapes for client sites. */
public final class ClientSiteDtos {

    private ClientSiteDtos() {
    }

    @Schema(name = "ClientSite", description = "A client location where workers are deployed")
    public record Response(
            Long id,
            Long clientId,
            String clientName,
            String siteCode,
            String siteName,
            @Schema(description = "\"PUNE-PLANT-1 — Pune Manufacturing Unit\"")
            String displayLabel,

            String addressLine1,
            String addressLine2,
            String city,
            String state,
            @Schema(description = "Governs minimum wage, professional tax and licensing for "
                    + "this site - can differ from the client's billing state")
            String stateCode,
            String pincode,

            String siteInchargeName,
            String siteInchargePhone,
            String siteInchargeEmail,

            boolean active) {
    }

    @Schema(name = "ClientSiteRequest", description = "Create or update a client site")
    public record Request(

            @NotBlank(message = "Site code is required")
            @Size(max = 40)
            // Case-insensitive on input: ClientSiteService upper-cases the value,
            // so rejecting "pune-ho" would make that normalisation unreachable
            // and the error message a pointless hurdle.
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_\\-]{1,39}$",
                    message = "must be letters, digits, hyphens or underscores, "
                            + "e.g. PUNE-PLANT-1 (stored upper case)")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "PUNE-PLANT-1")
            String siteCode,

            @NotBlank(message = "Site name is required")
            @Size(max = 160)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Pune Manufacturing Unit")
            String siteName,

            @Size(max = 200)
            String addressLine1,

            @Size(max = 200)
            String addressLine2,

            @Size(max = 100)
            String city,

            @Size(max = 100)
            String state,

            @Pattern(regexp = IndianFormats.GST_STATE_CODE,
                    message = IndianFormats.GST_STATE_CODE_MESSAGE)
            @Schema(description = "The site's own state code. Drives minimum wage and PT for "
                    + "workers here, so it must be the state the site is IN.",
                    example = "27")
            String stateCode,

            @Pattern(regexp = IndianFormats.PINCODE, message = IndianFormats.PINCODE_MESSAGE)
            String pincode,

            @Size(max = 120)
            String siteInchargeName,

            @Pattern(regexp = IndianFormats.MOBILE, message = IndianFormats.MOBILE_MESSAGE)
            String siteInchargePhone,

            @Email(message = "must be a valid email address")
            @Size(max = 180)
            String siteInchargeEmail,

            @Schema(defaultValue = "true")
            Boolean active) {
    }
}
