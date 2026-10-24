package com.hrsolution.worker.dto;

import com.hrsolution.common.validation.IndianFormats;
import com.hrsolution.worker.entity.Gender;
import com.hrsolution.worker.entity.PoliceVerificationStatus;
import com.hrsolution.worker.entity.WorkerStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Request and response shapes for workers.
 *
 * <h2>Why there are two response records</h2>
 *
 * <p>{@link Response} carries {@code aadhaarMasked} and {@code panMasked} and
 * nothing more. {@link SensitiveResponse} carries the real numbers and is
 * returned only from a separate endpoint gated on
 * {@code WORKER_SENSITIVE_READ}.
 *
 * <p>The alternative - one record whose fields are masked or not depending on
 * the caller - was rejected deliberately. It makes every response shape
 * conditional on a permission check somewhere else, so a reviewer cannot tell
 * by reading the DTO whether an Aadhaar can reach the wire. Two records mean
 * the unmasked numbers appear in exactly one place, and that place is trivially
 * greppable.
 */
public final class WorkerDtos {

    private WorkerDtos() {
    }

    // ==================================================================
    // Standard - masked
    // ==================================================================

    @Schema(name = "Worker", description = "A worker. Aadhaar and PAN are masked.")
    public record Response(
            Long id,
            @Schema(description = "Derived from the joining year and the id",
                    example = "EMP-2026-00042")
            String employeeCode,

            String firstName,
            String lastName,
            String fullName,
            String fatherOrHusbandName,

            LocalDate dateOfBirth,
            @Schema(description = "Age in whole years today")
            Integer age,
            Gender gender,
            LocalDate dateOfJoining,
            @Schema(description = "Completed months of service")
            Integer serviceMonths,

            String phone,
            String email,

            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String pincode,

            @Schema(description = "Masked. GET /workers/{id}/sensitive returns the real value "
                    + "to a caller holding WORKER_SENSITIVE_READ.",
                    example = "XXXX-XXXX-1234")
            String aadhaarMasked,
            @Schema(description = "Masked, as above", example = "XXXXXX234A")
            String panMasked,
            @Schema(description = "True when an Aadhaar is on file at all")
            boolean aadhaarOnFile,

            @Schema(description = "PF universal account number. Not secret - it is on every "
                    + "monthly return.")
            String uan,
            String esicIpNumber,

            WorkerStatus status,
            @Schema(description = "True while the worker is still employed")
            boolean employed,

            PoliceVerificationStatus policeVerificationStatus,
            LocalDate policeVerificationDate,
            @Schema(description = "True when verification permits deployment to a security role")
            boolean clearedForSecurityWork,

            String emergencyContactName,
            String emergencyContactPhone,
            String emergencyContactRelation,

            LocalDate lastWorkingDay,
            String exitReason,
            boolean fullAndFinalSettled,

            @Schema(description = "The deployment in force today, when there is one")
            Long currentDeploymentId,
            String currentDeploymentLabel,

            @Schema(description = "Documents uploaded, and how many are still unverified")
            int documentCount,
            int documentsPendingVerification,

            String notes,
            Instant createdAt) {
    }

    @Schema(name = "WorkerSummary", description = "A worker trimmed to a table row")
    public record SummaryResponse(
            Long id,
            String employeeCode,
            String fullName,
            String phone,
            WorkerStatus status,
            PoliceVerificationStatus policeVerificationStatus,
            LocalDate dateOfJoining,
            @Schema(description = "Where they are posted today, or null when on the bench")
            String currentDeploymentLabel) {
    }

    // ==================================================================
    // Sensitive - the real numbers
    // ==================================================================

    /**
     * The unmasked identifiers.
     *
     * <p>Returned by one endpoint only, gated on {@code WORKER_SENSITIVE_READ}
     * (SUPER_ADMIN, ADMIN and ACCOUNTS), and every call is written to the audit
     * trail. Needed because the PF and ESI returns, and the bank transfer file,
     * all require the real numbers - masking them would make the statutory
     * filings impossible.
     */
    @Schema(name = "WorkerSensitiveDetail",
            description = "Unmasked Aadhaar and PAN. Requires WORKER_SENSITIVE_READ; every "
                    + "access is audited.")
    public record SensitiveResponse(
            Long id,
            String employeeCode,
            String fullName,
            @Schema(example = "234512341234") String aadhaar,
            @Schema(example = "AABCS1234A") String pan,
            String uan,
            String esicIpNumber) {
    }

    // ==================================================================
    // Writes
    // ==================================================================

    @Schema(name = "WorkerRequest", description = "Create or update a worker")
    public record Request(

            @NotBlank(message = "First name is required")
            @Size(max = 100)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Rakesh")
            String firstName,

            @Size(max = 100)
            @Schema(example = "Shinde")
            String lastName,

            @Size(max = 160)
            @Schema(description = "Required on the CLRA and PF registers",
                    example = "Vitthal Shinde")
            String fatherOrHusbandName,

            @Past(message = "must be in the past")
            @Schema(description = "The worker must be 18 or over on their joining date",
                    example = "1995-06-12")
            LocalDate dateOfBirth,

            @Schema(description = "Collected because Form XII and the ESIC returns break "
                    + "headcount down this way")
            Gender gender,

            @NotNull(message = "A joining date is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-11-01")
            LocalDate dateOfJoining,

            @Pattern(regexp = IndianFormats.MOBILE, message = IndianFormats.MOBILE_MESSAGE)
            @Schema(example = "9876543210")
            String phone,

            @Email(message = "must be a valid email address")
            @Size(max = 180)
            String email,

            @Size(max = 200) String addressLine1,
            @Size(max = 200) String addressLine2,
            @Size(max = 100) String city,
            @Size(max = 100) String state,

            @Pattern(regexp = IndianFormats.PINCODE, message = IndianFormats.PINCODE_MESSAGE)
            String pincode,

            @Pattern(regexp = IndianFormats.AADHAAR, message = IndianFormats.AADHAAR_MESSAGE)
            @Schema(description = "Encrypted at rest and returned masked. A second worker with "
                    + "the same number is rejected with 409.",
                    example = "234512341234")
            String aadhaar,

            @Pattern(regexp = IndianFormats.PAN, message = IndianFormats.PAN_MESSAGE)
            @Schema(description = "Encrypted at rest and returned masked",
                    example = "AABCS1234A")
            String pan,

            @Pattern(regexp = IndianFormats.UAN, message = IndianFormats.UAN_MESSAGE)
            @Schema(example = "100123456789")
            String uan,

            @Pattern(regexp = IndianFormats.ESIC_IP, message = IndianFormats.ESIC_IP_MESSAGE)
            @Schema(example = "3100123456")
            String esicIpNumber,

            @Size(max = 160) String emergencyContactName,

            @Pattern(regexp = IndianFormats.MOBILE, message = IndianFormats.MOBILE_MESSAGE)
            String emergencyContactPhone,

            @Size(max = 60)
            @Schema(example = "Brother")
            String emergencyContactRelation,

            @Size(max = 2000)
            @Schema(description = "Internal notes")
            String notes) {
    }

    @Schema(name = "PoliceVerificationRequest",
            description = "Record the outcome of a police verification")
    public record PoliceVerificationRequest(

            @NotNull(message = "A status is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "VERIFIED or NOT_REQUIRED permit deployment to a security "
                            + "role; FAILED blocks it permanently.")
            PoliceVerificationStatus status,

            @Schema(description = "When the certificate was issued", example = "2026-10-20")
            LocalDate verifiedOn) {
    }

    @Schema(name = "WorkerExitRequest", description = "Record a worker leaving")
    public record ExitRequest(

            @NotNull(message = "A status is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "RESIGNED or TERMINATED")
            WorkerStatus status,

            @NotNull(message = "A last working day is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-12-31")
            LocalDate lastWorkingDay,

            @NotBlank(message = "A reason is required")
            @Size(max = 500)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    example = "Resigned to move back to his home district")
            String reason) {
    }

    @Schema(name = "WorkerFromApplicationRequest",
            description = "Convert a JOINED application into a worker")
    public record FromApplicationRequest(

            @NotNull(message = "A joining date is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "The worker's actual first day", example = "2026-11-01")
            LocalDate dateOfJoining,

            @Size(max = 160)
            @Schema(description = "Not held on a candidate record, but required on the "
                    + "statutory registers")
            String fatherOrHusbandName,

            @Past(message = "must be in the past")
            LocalDate dateOfBirth,

            Gender gender,

            @Pattern(regexp = IndianFormats.AADHAAR, message = IndianFormats.AADHAAR_MESSAGE)
            String aadhaar,

            @Pattern(regexp = IndianFormats.PAN, message = IndianFormats.PAN_MESSAGE)
            String pan) {
    }
}
