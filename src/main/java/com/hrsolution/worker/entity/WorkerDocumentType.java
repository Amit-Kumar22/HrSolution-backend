package com.hrsolution.worker.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * What a stored worker document is.
 *
 * <p>{@link #expiryExpected} drives the Phase 10 reminders: a police
 * verification or medical certificate lapses and has to be renewed, whereas an
 * Aadhaar copy does not. Asking for an expiry date on a document that cannot
 * expire trains people to type nonsense into it.
 */
@Getter
@RequiredArgsConstructor
public enum WorkerDocumentType {

    AADHAAR("Aadhaar card", false),

    PAN("PAN card", false),

    PHOTO("Photograph", false),

    /** Mandatory before deploying a security guard under the PSAR Act. */
    POLICE_VERIFICATION("Police verification certificate", true),

    EDUCATION("Education certificate", false),

    EXPERIENCE("Experience letter", false),

    /** Fitness certificates are typically annual. */
    MEDICAL("Medical fitness certificate", true),

    /** A passbook page or cancelled cheque, to verify the bank account. */
    BANK_PROOF("Bank account proof", false),

    /** Driving licence, trade certificate, gun licence - all renewable. */
    LICENCE("Licence or trade certificate", true),

    OTHER("Other", false);

    private final String displayName;

    /** Whether an expiry date is expected, and reminders should be sent. */
    private final boolean expiryExpected;
}
