package com.hrsolution.worker.entity;

/** Whether a member of staff has checked a worker document against the original. */
public enum DocumentVerificationStatus {

    /** Uploaded, nobody has checked it. */
    PENDING,

    /** Checked against the original and accepted. */
    VERIFIED,

    /** Illegible, expired, or not the document it claims to be. */
    REJECTED;

    public boolean isDecided() {
        return this != PENDING;
    }

    /** Whether this document counts as evidence for compliance purposes. */
    public boolean countsAsEvidence() {
        return this == VERIFIED;
    }
}
