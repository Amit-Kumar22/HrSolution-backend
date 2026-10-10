package com.hrsolution.client.dto;

import com.hrsolution.client.entity.ClientStatus;
import com.hrsolution.client.entity.GstTreatment;
import com.hrsolution.common.validation.IndianFormats;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** Request and response shapes for client companies. */
public final class ClientDtos {

    private ClientDtos() {
    }

    @Schema(name = "Client", description = "A client company")
    public record Response(
            Long id,
            @Schema(description = "Derived from the id; stable and human-quotable", example = "CLI-00042")
            String clientCode,
            String legalName,
            String tradeName,
            @Schema(description = "Trade name when set, otherwise the legal name")
            String displayName,

            String gstin,
            String pan,
            String cin,

            String billingAddressLine1,
            String billingAddressLine2,
            String billingCity,
            String billingState,
            @Schema(description = "Two-digit GST state code", example = "27")
            String billingStateCode,
            String billingPincode,
            String billingCountry,

            Long industryId,
            String industryName,

            ClientStatus status,
            int paymentTermsDays,
            LocalDate onboardedOn,
            String notes,

            @Schema(description = """
                    Whether an invoice to this client attracts CGST+SGST (same state as the \
                    company) or IGST (different state). UNKNOWN means a state code is missing \
                    on either side, and Phase 9 will refuse to invoice rather than guess.""")
            GstTreatment gstTreatment,

            @Schema(description = "Counts for the client overview screen")
            int activeSiteCount,
            int activeContractCount) {
    }

    @Schema(name = "ClientSummary", description = "A client in a list, trimmed to what a table row shows")
    public record SummaryResponse(
            Long id,
            String clientCode,
            String legalName,
            String displayName,
            String gstin,
            String billingCity,
            String billingState,
            ClientStatus status,
            GstTreatment gstTreatment,
            LocalDate onboardedOn) {
    }

    @Schema(name = "ClientRequest", description = "Create or update a client company")
    public record Request(

            @NotBlank(message = "Legal name is required")
            @Size(max = 200)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    example = "Bharat Textiles Private Limited")
            String legalName,

            @Size(max = 200)
            @Schema(example = "Bharat Textiles")
            String tradeName,

            @Pattern(regexp = IndianFormats.GSTIN, message = IndianFormats.GSTIN_MESSAGE)
            @Schema(description = "Optional at onboarding, but required before an invoice can "
                    + "be issued in Phase 9",
                    example = "27AABCB1234C1Z5")
            String gstin,

            @Pattern(regexp = IndianFormats.PAN, message = IndianFormats.PAN_MESSAGE)
            String pan,

            @Pattern(regexp = IndianFormats.CIN, message = IndianFormats.CIN_MESSAGE)
            String cin,

            @Size(max = 200)
            String billingAddressLine1,

            @Size(max = 200)
            String billingAddressLine2,

            @Size(max = 100)
            String billingCity,

            @Size(max = 100)
            String billingState,

            @Pattern(regexp = IndianFormats.GST_STATE_CODE,
                    message = IndianFormats.GST_STATE_CODE_MESSAGE)
            @Schema(description = "Decides CGST+SGST vs IGST on every invoice. Get this right.",
                    example = "27")
            String billingStateCode,

            @Pattern(regexp = IndianFormats.PINCODE, message = IndianFormats.PINCODE_MESSAGE)
            String billingPincode,

            @Size(max = 100)
            String billingCountry,

            @Schema(description = "Optional link to an industry from the master list")
            Long industryId,

            @Min(value = 0, message = "must be 0 or more days")
            @Max(value = 365, message = "must be 365 days or fewer")
            @Schema(description = "Default credit period; a contract may override it",
                    defaultValue = "30")
            Integer paymentTermsDays,

            @Size(max = 2000)
            String notes) {
    }

    @Schema(name = "ClientStatusRequest", description = "Change a client's status, with a reason for the audit trail")
    public record StatusChangeRequest(

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            ClientStatus status,

            @Size(max = 500)
            @Schema(description = "Recorded in the audit log and appended to the client notes")
            String reason) {
    }

    // ---------------- Client users ----------------

    @Schema(name = "ClientUser", description = "A person at the client who can sign in to the portal")
    public record UserResponse(
            Long id,
            Long userId,
            String email,
            String fullName,
            String phone,
            String designation,
            @Schema(description = "Receives contract and invoice notifications")
            boolean primaryContact,
            boolean active,
            @Schema(description = "Status of the underlying login")
            String userStatus) {
    }

    @Schema(name = "ClientLinkUserRequest", description = "Invite an existing user to a client, or create the link")
    public record LinkUserRequest(

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Id of an existing user holding the CLIENT role")
            Long userId,

            @Size(max = 120)
            @Schema(example = "Plant Head")
            String designation,

            @Schema(description = "Makes this person the primary contact, clearing any other",
                    defaultValue = "false")
            Boolean primaryContact) {
    }

    @Schema(name = "ClientApproveRegistrationRequest", description = "Approve a pending client registration and create the client record")
    public record ApproveRegistrationRequest(

            @NotBlank(message = "Legal name is required")
            @Size(max = 200)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Defaults to the company name the registrant typed, but should "
                            + "be corrected to the registered legal name before approving")
            String legalName,

            @Pattern(regexp = IndianFormats.GSTIN, message = IndianFormats.GSTIN_MESSAGE)
            String gstin,

            @Pattern(regexp = IndianFormats.PAN, message = IndianFormats.PAN_MESSAGE)
            String pan,

            @Size(max = 100)
            String billingCity,

            @Size(max = 100)
            String billingState,

            @Pattern(regexp = IndianFormats.GST_STATE_CODE,
                    message = IndianFormats.GST_STATE_CODE_MESSAGE)
            String billingStateCode,

            @Size(max = 120)
            @Schema(example = "Purchase Manager")
            String designation,

            @Min(0) @Max(365)
            Integer paymentTermsDays) {
    }

    @Schema(name = "ClientDetail", description = "A client plus its sites and contracts, for the overview screen")
    public record DetailResponse(
            Response client,
            List<UserResponse> users,
            List<ClientSiteDtos.Response> sites,
            List<ClientContractDtos.Response> contracts) {
    }
}
