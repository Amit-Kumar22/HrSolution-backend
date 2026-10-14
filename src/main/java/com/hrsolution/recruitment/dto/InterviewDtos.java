package com.hrsolution.recruitment.dto;

import com.hrsolution.recruitment.entity.InterviewMode;
import com.hrsolution.recruitment.entity.InterviewResult;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Request and response shapes for interviews. */
public final class InterviewDtos {

    private InterviewDtos() {
    }

    @Schema(name = "Interview", description = "A scheduled conversation about one application")
    public record Response(
            Long id,

            Long applicationId,
            @Schema(description = "Denormalised so an interview list needs no second call")
            String candidateName,
            String jobTitle,

            int roundNumber,
            Instant scheduledAt,
            Integer durationMinutes,

            InterviewMode mode,
            @Schema(description = "An address for IN_PERSON and PHONE, a joining URL for VIDEO")
            String venueOrLink,

            Long interviewerUserId,
            String interviewerName,

            InterviewResult result,
            Integer rating,
            @Schema(description = "The panel's write-up. Never shown to the candidate.")
            String feedback,

            @Schema(description = "True when the slot is still ahead of us")
            boolean upcoming,
            @Schema(description = "True when the slot has passed with no result recorded - "
                    + "the number a recruitment dashboard leads with")
            boolean awaitingFeedback) {
    }

    @Schema(name = "InterviewRequest", description = "Schedule or reschedule an interview")
    public record Request(

            @NotNull(message = "A scheduled time is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "ISO-8601 UTC. Must be in the future when scheduling.",
                    example = "2026-11-02T09:30:00Z")
            Instant scheduledAt,

            @Min(value = 5, message = "must be at least 5 minutes")
            @Max(value = 480, message = "must be 8 hours or fewer")
            @Schema(description = "Defaults to 30 when omitted", example = "45")
            Integer durationMinutes,

            @NotNull(message = "A mode is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "IN_PERSON, PHONE or VIDEO")
            InterviewMode mode,

            @Size(max = 400)
            @Schema(description = "Required for IN_PERSON and VIDEO: an address or a joining "
                    + "link. A candidate cannot attend an interview with no location.",
                    example = "Office 4, MIDC Bhosari, Pune")
            String venueOrLink,

            @Schema(description = "Who will conduct it. A staff user id.")
            Long interviewerUserId,

            @Min(value = 1, message = "must be 1 or greater")
            @Max(value = 20, message = "must be 20 or fewer")
            @Schema(description = "Omit to append the next round automatically",
                    example = "1")
            Integer roundNumber) {
    }

    @Schema(name = "InterviewResultRequest", description = "Record the outcome of an interview")
    public record ResultRequest(

            @NotNull(message = "A result is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "PASS, FAIL or NO_SHOW. NO_SHOW is tracked separately from "
                            + "FAIL because it measures the confirmation process, not the "
                            + "candidate.")
            InterviewResult result,

            @Min(value = 1, message = "must be between 1 and 5")
            @Max(value = 5, message = "must be between 1 and 5")
            @Schema(example = "4")
            Integer rating,

            @Size(max = 2000)
            @Schema(description = "The panel's write-up. Never shown to the candidate.")
            String feedback,

            @Schema(description = "When true and the result is PASS, the application is moved "
                    + "to SELECTED in the same call. Leave false to decide separately.",
                    defaultValue = "false")
            Boolean advanceApplication) {
    }
}
