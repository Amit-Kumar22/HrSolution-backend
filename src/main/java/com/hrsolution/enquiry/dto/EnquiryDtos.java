package com.hrsolution.enquiry.dto;

import com.hrsolution.common.validation.IndianFormats;
import com.hrsolution.enquiry.entity.EnquiryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

/** Request and response shapes for manpower enquiries. */
public final class EnquiryDtos {

    private EnquiryDtos() {
    }

    /**
     * The public manpower requirement form.
     *
     * <p>Only company name, contact person and phone are mandatory. Everything
     * else is optional on purpose: this form is the top of the sales funnel, and
     * rejecting a lead because the visitor had not decided a start date costs
     * real business. The gaps get filled in on the follow-up call.
     */
    @Schema(description = "Public manpower requirement enquiry")
    public record SubmitRequest(

            @NotBlank(message = "Company name is required")
            @Size(max = 200)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    example = "Bharat Textiles Private Limited")
            String companyName,

            @NotBlank(message = "Contact person is required")
            @Size(max = 120)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Rajesh Kulkarni")
            String contactPerson,

            @NotBlank(message = "Phone number is required")
            @Pattern(regexp = IndianFormats.MOBILE, message = IndianFormats.MOBILE_MESSAGE)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "9876543210")
            String phone,

            @Email(message = "must be a valid email address")
            @Size(max = 180)
            @Schema(example = "rajesh@bharattextiles.example.com")
            String email,

            @Size(max = 100)
            @Schema(example = "Pune")
            String city,

            @Size(max = 100)
            @Schema(example = "Maharashtra")
            String state,

            @Schema(description = "Id from GET /public/manpower-categories, if one fits")
            Long categoryId,

            @Size(max = 120)
            @Schema(description = "Free text when no listed category fits",
                    example = "Forklift operators")
            String otherCategory,

            @Min(value = 1, message = "must be at least 1")
            @Max(value = 100000, message = "must be 100000 or fewer")
            @Schema(example = "25")
            Integer numberOfWorkers,

            @Min(value = 1, message = "must be at least 1 month")
            @Max(value = 600, message = "must be 600 months or fewer")
            @Schema(example = "12")
            Integer durationMonths,

            // Future, not FutureOrPresent: a requirement starting today would
            // already have been phoned in, and a past date is a typo.
            @Future(message = "must be a future date")
            @Schema(example = "2026-12-01")
            LocalDate requiredFrom,

            @Size(max = 2000)
            String message,

            @Schema(description = "Honeypot. A hidden field that humans never fill. "
                    + "Leave it absent or empty; anything else flags the submission as spam.",
                    example = "")
            String website,

            @Schema(description = "Epoch millis when the form was rendered. Used to detect "
                    + "submissions too fast to be human. Optional.")
            Long formRenderedAt) {
    }

    /** What the public form gets back. Deliberately minimal. */
    @Schema(description = "Acknowledgement of a submitted enquiry")
    public record SubmitResponse(
            @Schema(description = "Reference the enquirer can quote when following up",
                    example = "ENQ-000042")
            String reference,
            String message) {
    }

    @Schema(description = "An enquiry, for staff")
    public record Response(
            Long id,
            @Schema(example = "ENQ-000042") String reference,
            String companyName,
            String contactPerson,
            String phone,
            String email,
            String city,
            String state,
            Long categoryId,
            @Schema(description = "The category name, or the free text, or \"Not specified\"")
            String categoryLabel,
            Integer numberOfWorkers,
            Integer durationMonths,
            LocalDate requiredFrom,
            String message,
            EnquiryStatus status,
            Long assignedToUserId,
            String assignedToName,
            String internalNotes,
            Instant createdAt,
            Instant updatedAt) {
    }

    @Schema(description = "Move an enquiry along the pipeline")
    public record UpdateStatusRequest(

            @NotNull(message = "Status is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            EnquiryStatus status,

            @Size(max = 1000)
            @Schema(description = "Appended to the internal notes with a timestamp and your name")
            String note) {
    }

    @Schema(description = "Assign an enquiry to a staff member")
    public record AssignRequest(

            @Schema(description = "User id to assign to, or null to unassign")
            Long assignedToUserId,

            @Size(max = 1000)
            String note) {
    }
}
