package com.hrsolution.recruitment.dto;

import com.hrsolution.common.validation.IndianFormats;
import com.hrsolution.recruitment.entity.CandidateSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Request and response shapes for candidates.
 *
 * <p>{@link Response} carries {@code notes} and the spam flags, which the
 * candidate's own portal must never see. The self-service endpoints therefore
 * return {@link SelfResponse} instead - again two records rather than one with
 * conditional mapping, so that the separation cannot be undone by editing an
 * annotation.
 */
public final class CandidateDtos {

    private CandidateDtos() {
    }

    // ==================================================================
    // Staff
    // ==================================================================

    @Schema(name = "Candidate", description = "A person in the hiring pipeline, as staff see them")
    public record Response(
            Long id,
            String fullName,
            String email,
            String phone,

            String city,
            String state,

            Integer totalExperienceMonths,
            @Schema(description = "\"3 years 6 months\", or \"Fresher\"")
            String experienceLabel,
            String highestQualification,

            Long preferredCategoryId,
            String preferredCategoryName,

            @Schema(description = "True when the candidate can sign in and track applications")
            boolean hasLogin,
            @Schema(description = "The linked login, when there is one")
            Long userId,

            @Schema(description = "Id of the current CV, for GET /documents/{id}/download")
            Long resumeDocumentId,
            String resumeFileName,

            CandidateSource source,

            @Schema(description = "DPDP Act consent. False means we hold this record without "
                    + "a lawful basis and it should be purged.")
            boolean consentGiven,
            Instant consentGivenAt,

            @Schema(description = "Flagged by the honeypot or timing checks, not rejected")
            boolean spamSuspected,
            String spamReason,

            @Schema(description = "Internal. Never returned by the candidate's own endpoints.")
            String notes,

            @Schema(description = "How many applications this person has submitted")
            long applicationCount,

            Instant createdAt) {
    }

    @Schema(name = "CandidateSummary", description = "A candidate trimmed to a table row")
    public record SummaryResponse(
            Long id,
            String fullName,
            String email,
            String phone,
            String city,
            String experienceLabel,
            String preferredCategoryName,
            CandidateSource source,
            boolean hasLogin,
            boolean hasResume,
            boolean spamSuspected,
            long applicationCount,
            Instant createdAt) {
    }

    @Schema(name = "CandidateRequest", description = "Create or update a candidate (staff)")
    public record Request(

            @NotBlank(message = "Full name is required")
            @Size(max = 160)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Rakesh Shinde")
            String fullName,

            @NotBlank(message = "Email is required")
            @Email(message = "must be a valid email address")
            @Size(max = 180)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "The identity key. A second candidate with the same address "
                            + "is rejected with 409 - edit the existing one instead.",
                    example = "rakesh.shinde@example.com")
            String email,

            @Pattern(regexp = IndianFormats.MOBILE, message = IndianFormats.MOBILE_MESSAGE)
            @Schema(example = "9876543210")
            String phone,

            @Size(max = 100)
            @Schema(example = "Pune")
            String city,

            @Size(max = 100)
            @Schema(example = "Maharashtra")
            String state,

            @Min(value = 0, message = "cannot be negative")
            @Max(value = 720, message = "must be 60 years or fewer")
            @Schema(description = "Months. 0 means fresher.", example = "42")
            Integer totalExperienceMonths,

            @Size(max = 120)
            @Schema(example = "ITI Electrician")
            String highestQualification,

            @Schema(description = "Id from GET /manpower-categories")
            Long preferredCategoryId,

            @Schema(description = "How we met this person. Drives cost-per-hire reporting.",
                    defaultValue = "WALK_IN")
            CandidateSource source,

            @Schema(description = "DPDP Act consent to hold and process this CV. Staff entering "
                    + "a walk-in must confirm it was given.", defaultValue = "false")
            Boolean consentGiven,

            @Size(max = 2000)
            @Schema(description = "Internal notes. Never shown to the candidate.")
            String notes) {
    }

    // ==================================================================
    // The candidate's own portal
    // ==================================================================

    @Schema(name = "CandidateSelfProfile",
            description = "The signed-in candidate's own profile. No internal notes, no spam flags.")
    public record SelfResponse(
            Long id,
            String fullName,
            String email,
            String phone,
            String city,
            String state,
            Integer totalExperienceMonths,
            String experienceLabel,
            String highestQualification,
            Long preferredCategoryId,
            String preferredCategoryName,
            Long resumeDocumentId,
            String resumeFileName,
            boolean consentGiven,
            long applicationCount) {
    }

    @Schema(name = "CandidateSelfProfileRequest",
            description = "Update your own candidate profile")
    public record SelfRequest(

            @NotBlank(message = "Full name is required")
            @Size(max = 160)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Rakesh Shinde")
            String fullName,

            @Pattern(regexp = IndianFormats.MOBILE, message = IndianFormats.MOBILE_MESSAGE)
            @Schema(example = "9876543210")
            String phone,

            @Size(max = 100)
            String city,

            @Size(max = 100)
            String state,

            @Min(value = 0, message = "cannot be negative")
            @Max(value = 720, message = "must be 60 years or fewer")
            Integer totalExperienceMonths,

            @Size(max = 120)
            String highestQualification,

            Long preferredCategoryId,

            // Email is absent on purpose: it is the identity key for the
            // pipeline and the link to the login. Changing it would silently
            // re-point or orphan an application history, so there is no
            // endpoint for it.
            @AssertTrue(message = "Consent is required to keep your profile and CV on file")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Must be true. Withdrawing consent is a deletion request, "
                            + "not a profile update.")
            Boolean consentGiven) {
    }
}
