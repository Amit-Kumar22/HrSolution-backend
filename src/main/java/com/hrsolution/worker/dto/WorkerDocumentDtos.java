package com.hrsolution.worker.dto;

import com.hrsolution.worker.entity.DocumentVerificationStatus;
import com.hrsolution.worker.entity.WorkerDocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

/** Request and response shapes for worker documents. */
public final class WorkerDocumentDtos {

    private WorkerDocumentDtos() {
    }

    @Schema(name = "WorkerDocument", description = "A document held on file for a worker")
    public record Response(
            Long id,
            Long workerId,

            WorkerDocumentType documentType,
            @Schema(description = "\"Police verification certificate\"")
            String documentTypeLabel,

            @Schema(description = "The stored file. Download with "
                    + "GET /documents/{documentId}/download.")
            Long documentId,
            String fileName,
            long sizeBytes,

            @Schema(description = "The number on the certificate. NOT the Aadhaar or PAN - "
                    + "those are encrypted on the worker record.")
            String referenceNumber,

            DocumentVerificationStatus verificationStatus,
            Long verifiedByUserId,
            String verifiedByName,
            Instant verifiedAt,
            String rejectionReason,

            LocalDate issuedOn,
            LocalDate expiresOn,
            @Schema(description = "True when the expiry date has passed")
            boolean expired,
            @Schema(description = "Days until it lapses, or null when it has no expiry")
            Long daysUntilExpiry,
            @Schema(description = "True only when verified AND not expired. An unverified "
                    + "document proves nothing; an expired one proved something once.")
            boolean validEvidence,
            @Schema(description = "True when this type normally expires but no date was given")
            boolean missingExpectedExpiry,

            Instant createdAt) {
    }

    @Schema(name = "WorkerDocumentUploadRequest",
            description = "Metadata for a worker document upload")
    public record UploadRequest(

            @NotNull(message = "A document type is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "POLICE_VERIFICATION, MEDICAL and LICENCE normally expire, "
                            + "so supply expiresOn for those")
            WorkerDocumentType documentType,

            @Size(max = 80)
            @Schema(description = "The certificate's own number", example = "PV/PUN/2026/4471")
            String referenceNumber,

            @Schema(example = "2026-10-20")
            LocalDate issuedOn,

            @Schema(description = "Drives the renewal reminders", example = "2027-10-19")
            LocalDate expiresOn) {
    }

    @Schema(name = "WorkerDocumentVerificationRequest",
            description = "Accept or reject a worker document")
    public record VerificationRequest(

            @NotNull(message = "A decision is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "VERIFIED or REJECTED")
            DocumentVerificationStatus status,

            @Size(max = 500)
            @Schema(description = "Required when rejecting",
                    example = "Illegible - please re-scan at a higher resolution")
            String reason) {
    }
}
