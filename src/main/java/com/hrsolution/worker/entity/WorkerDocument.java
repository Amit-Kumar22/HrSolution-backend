package com.hrsolution.worker.entity;

import com.hrsolution.common.domain.SoftDeletableEntity;
import com.hrsolution.document.entity.StoredDocument;
import com.hrsolution.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * A document held on file for a worker.
 *
 * <p>The file itself is a {@link StoredDocument} from Phase 3, so storage,
 * checksums, content-type detection by magic bytes and the authenticated
 * download endpoint are all reused. This entity adds what the file
 * <em>means</em>: its type, whether anyone has checked it, and when it lapses.
 *
 * <p>{@link #referenceNumber} is the number printed on the certificate, not the
 * Aadhaar or PAN itself. Those live encrypted on {@link Worker}, and copying
 * them here in clear would defeat that entirely - a point worth remembering
 * when someone asks why the Aadhaar number is not on the Aadhaar document row.
 */
@Entity
@Table(name = "worker_documents")
@Getter
@Setter
public class WorkerDocument extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private StoredDocument document;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private WorkerDocumentType documentType;

    /** The certificate's own number. NOT the Aadhaar or PAN - see the class note. */
    @Column(name = "reference_number", length = 80)
    private String referenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private DocumentVerificationStatus verificationStatus = DocumentVerificationStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_user_id")
    private User verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "issued_on")
    private LocalDate issuedOn;

    /** Drives the Phase 10 expiry reminders. */
    @Column(name = "expires_on")
    private LocalDate expiresOn;

    // ------------------------------------------------------------------

    public boolean hasExpiredOn(LocalDate date) {
        return expiresOn != null && date != null && date.isAfter(expiresOn);
    }

    /** Days until it lapses, or null when it has no expiry or already lapsed. */
    public Long daysUntilExpiry(LocalDate asOf) {
        if (expiresOn == null || asOf == null || expiresOn.isBefore(asOf)) {
            return null;
        }
        return ChronoUnit.DAYS.between(asOf, expiresOn);
    }

    /**
     * Whether this document counts as compliance evidence on a date.
     *
     * <p>Both halves matter. An unverified document proves nothing - anyone can
     * upload a photo. An expired one proved something once and no longer does.
     */
    public boolean isValidEvidenceOn(LocalDate date) {
        return verificationStatus.countsAsEvidence() && !hasExpiredOn(date);
    }

    /** Records a verification decision, clearing whatever the last one left. */
    public void recordVerification(DocumentVerificationStatus decision,
                                   User verifier,
                                   String reason) {
        this.verificationStatus = decision;
        this.verifiedBy = verifier;
        this.verifiedAt = Instant.now();
        this.rejectionReason = decision == DocumentVerificationStatus.REJECTED ? reason : null;
    }

    /** Whether an expiry date should have been supplied for this type. */
    public boolean isMissingExpectedExpiry() {
        return documentType != null && documentType.isExpiryExpected() && expiresOn == null;
    }
}
