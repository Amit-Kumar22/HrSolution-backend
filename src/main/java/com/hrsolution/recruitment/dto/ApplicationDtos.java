package com.hrsolution.recruitment.dto;

import com.hrsolution.common.validation.IndianFormats;
import com.hrsolution.recruitment.entity.ApplicationStatus;
import com.hrsolution.recruitment.entity.CandidateSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Request and response shapes for applications and the funnel. */
public final class ApplicationDtos {

    private ApplicationDtos() {
    }

    // ==================================================================
    // Staff
    // ==================================================================

    @Schema(name = "Application", description = "One candidate against one job")
    public record Response(
            Long id,

            Long jobId,
            String jobSlug,
            String jobTitle,
            String categoryName,

            Long candidateId,
            String candidateName,
            String candidateEmail,
            String candidatePhone,

            ApplicationStatus status,
            @Schema(description = "The statuses this application may move to next. Drive the "
                    + "status dropdown from this rather than from the full enum.")
            List<ApplicationStatus> allowedNextStatuses,
            @Schema(description = "True when the application is finished with, either way")
            boolean terminal,

            LocalDate appliedOn,
            @Schema(description = "Days since it was submitted, for the ageing column")
            long daysInPipeline,

            CandidateSource source,
            String coverNote,
            BigDecimal expectedSalary,

            @Schema(description = "The CV submitted with this application. Falls back to the "
                    + "candidate's current CV when the application carried none.")
            Long resumeDocumentId,
            String resumeFileName,

            Long assignedToUserId,
            String assignedToName,

            String rejectionReason,

            @Schema(description = "How many interviews have been scheduled")
            int interviewCount,
            @Schema(description = "True when an interview is scheduled in the future and "
                    + "still awaiting a result")
            boolean hasUpcomingInterview,

            Instant createdAt) {
    }

    @Schema(name = "ApplicationSummary", description = "An application trimmed to a board card")
    public record SummaryResponse(
            Long id,
            Long jobId,
            String jobTitle,
            Long candidateId,
            String candidateName,
            String candidatePhone,
            ApplicationStatus status,
            LocalDate appliedOn,
            long daysInPipeline,
            Long assignedToUserId,
            String assignedToName,
            boolean hasResume,
            int interviewCount) {
    }

    @Schema(name = "ApplicationDetail",
            description = "An application with its full funnel trail, for the detail drawer")
    public record DetailResponse(
            Response application,
            @Schema(description = "Oldest first. The first entry has a null fromStatus and "
                    + "records the submission.")
            List<StatusHistoryEntry> history,
            List<InterviewDtos.Response> interviews) {
    }

    @Schema(name = "ApplicationStatusHistoryEntry",
            description = "One recorded move through the funnel")
    public record StatusHistoryEntry(
            Long id,
            @Schema(description = "Null on the first entry, which records the submission")
            ApplicationStatus fromStatus,
            ApplicationStatus toStatus,
            @Schema(description = "Null when the candidate acted on their own application")
            Long changedByUserId,
            String changedByEmail,
            @Schema(description = "True when a member of staff made this move")
            boolean madeByStaff,
            String notes,
            Instant createdAt) {
    }

    @Schema(name = "JobPipelineSummary",
            description = "How many applications sit in each status for one job")
    public record PipelineSummary(
            Long jobId,
            String jobTitle,
            int openings,
            long openingsCommitted,
            long openingsRemaining,
            long totalApplications,
            @Schema(description = "Keyed by ApplicationStatus. Statuses with no applications "
                    + "are present with a zero, so the board can render every column.")
            Map<ApplicationStatus, Long> countsByStatus) {
    }

    @Schema(name = "ApplicationStatusRequest", description = "Move an application along the funnel")
    public record StatusChangeRequest(

            @NotNull(message = "A status is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Must be one of the current status's allowedNextStatuses. "
                            + "Anything else is a 422.")
            ApplicationStatus status,

            @Size(max = 1000)
            @Schema(description = "Appended to the funnel trail. Required when moving to "
                    + "REJECTED - a rejection with no reason is useless to the next recruiter.",
                    example = "Strong on experience but could not join before November")
            String note) {
    }

    @Schema(name = "ApplicationAssignRequest", description = "Assign an application to a recruiter")
    public record AssignRequest(

            @Schema(description = "A staff user id, or null to unassign")
            Long assignedToUserId,

            @Size(max = 1000)
            String note) {
    }

    @Schema(name = "StaffApplicationRequest",
            description = "Record an application on behalf of a candidate, e.g. a walk-in")
    public record StaffRequest(

            @NotNull(message = "A candidate is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Id from GET /candidates. Create the candidate first if they "
                            + "are new.")
            Long candidateId,

            @Schema(description = "How this application arrived", defaultValue = "WALK_IN")
            CandidateSource source,

            @Size(max = 2000)
            String coverNote,

            @DecimalMin(value = "0.0", message = "cannot be negative")
            @DecimalMax(value = "9999999.99", message = "is implausibly large")
            @Schema(example = "20000.00")
            BigDecimal expectedSalary,

            @Schema(description = "Recruiter to own it from the start")
            Long assignedToUserId,

            @Size(max = 1000)
            @Schema(description = "Recorded against the submission in the funnel trail")
            String note) {
    }

    // ==================================================================
    // Applying - public and signed-in candidate
    // ==================================================================

    /**
     * The anonymous apply form.
     *
     * <p>Carries the applicant's own details because there is no account to
     * read them from. {@code website} and {@code formRenderedAt} are the same
     * honeypot and timing pair the enquiry and contact forms use - a tripped
     * check stores the application flagged rather than rejecting it, because a
     * false positive here loses a real applicant.
     */
    @Schema(name = "PublicApplyRequest", description = "Apply for a job without an account")
    public record PublicApplyRequest(

            @NotBlank(message = "Your name is required")
            @Size(max = 160)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Rakesh Shinde")
            String fullName,

            @NotBlank(message = "Email is required")
            @Email(message = "must be a valid email address")
            @Size(max = 180)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    example = "rakesh.shinde@example.com")
            String email,

            @NotBlank(message = "A mobile number is required")
            @Pattern(regexp = IndianFormats.MOBILE, message = IndianFormats.MOBILE_MESSAGE)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "9876543210")
            String phone,

            @Size(max = 100)
            @Schema(example = "Pune")
            String city,

            @Size(max = 100)
            @Schema(example = "Maharashtra")
            String state,

            @Min(value = 0, message = "cannot be negative")
            @Max(value = 720, message = "must be 60 years or fewer")
            @Schema(description = "Months of experience. 0 if none.", example = "24")
            Integer totalExperienceMonths,

            @Size(max = 120)
            @Schema(example = "ITI Electrician")
            String highestQualification,

            @Size(max = 2000)
            @Schema(description = "Anything the applicant wants to add")
            String coverNote,

            @DecimalMin(value = "0.0", message = "cannot be negative")
            @DecimalMax(value = "9999999.99", message = "is implausibly large")
            @Schema(example = "20000.00")
            BigDecimal expectedSalary,

            @AssertTrue(message = "Consent is required to process your application")
            @NotNull(message = "Consent is required to process your application")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "DPDP Act consent to hold and process the application and CV. "
                            + "Must be true.")
            Boolean consentGiven,

            @Schema(description = "Honeypot. Render it hidden and leave it empty.", example = "")
            String website,

            @Schema(description = "Epoch millis when the form was rendered. Optional.")
            Long formRenderedAt) {
    }

    @Schema(name = "CandidateApplyRequest", description = "Apply for a job as a signed-in candidate")
    public record SelfApplyRequest(

            @Size(max = 2000)
            String coverNote,

            @DecimalMin(value = "0.0", message = "cannot be negative")
            @DecimalMax(value = "9999999.99", message = "is implausibly large")
            BigDecimal expectedSalary) {
    }

    @Schema(name = "ApplyResponse", description = "Acknowledgement of a submitted application")
    public record ApplyResponse(
            @Schema(description = "Quote this when following up", example = "APP-000123")
            String reference,
            Long applicationId,
            String jobTitle,
            ApplicationStatus status,
            String message) {
    }

    /**
     * What the candidate sees of their own application.
     *
     * <p>Notably absent: the recruiter it is assigned to, the internal notes on
     * the trail, the interviewer's feedback and rating. A candidate is entitled
     * to know where they stand, not to read the panel's write-up.
     */
    @Schema(name = "CandidateApplication",
            description = "The signed-in candidate's view of their own application")
    public record SelfResponse(
            Long id,
            String reference,
            Long jobId,
            String jobSlug,
            String jobTitle,
            String categoryName,
            String location,
            ApplicationStatus status,
            @Schema(description = "A plain-language version of the status, for the candidate",
                    example = "Shortlisted - we will be in touch about an interview")
            String statusLabel,
            LocalDate appliedOn,
            boolean terminal,
            @Schema(description = "True when the candidate may still withdraw")
            boolean withdrawable,
            @Schema(description = "Scheduled time of the next interview, when one is booked")
            Instant nextInterviewAt,
            BigDecimal expectedSalary,
            Long resumeDocumentId) {
    }

    @Schema(name = "WithdrawRequest", description = "Withdraw your own application")
    public record WithdrawRequest(

            @Size(max = 1000)
            @Schema(description = "Optional. Helps the recruiter understand why.",
                    example = "Accepted another offer")
            String reason) {
    }
}
