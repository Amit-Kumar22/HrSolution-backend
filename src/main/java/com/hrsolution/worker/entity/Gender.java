package com.hrsolution.worker.entity;

/**
 * Recorded because the statutory registers require it.
 *
 * <p>Form XII under the Contract Labour (Regulation and Abolition) Central
 * Rules and the ESIC returns both break headcount down this way, and the
 * Maternity Benefit Act obligations depend on it. It is not collected for
 * analytics.
 */
public enum Gender {

    MALE,

    FEMALE,

    /**
     * Recognised as a distinct legal category in India since NALSA v. Union of
     * India (2014), and accepted on statutory filings.
     */
    OTHER
}
