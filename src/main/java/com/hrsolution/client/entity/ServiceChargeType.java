package com.hrsolution.client.entity;

/**
 * How the service charge on a contract is calculated.
 *
 * <p>Both forms are common in Indian contract staffing, and the difference is
 * material: on a percentage deal the agency's revenue rises with wages, on a
 * fixed deal it does not. Stored explicitly rather than inferred from the
 * magnitude of the value, because "8" is a plausible percentage and "8" is also
 * a plausible (if small) per-worker fee.
 */
public enum ServiceChargeType {

    /** A percentage of the wage bill. {@code 8.5} means 8.5%. */
    PERCENTAGE,

    /** A flat amount per deployed worker per month. {@code 1200} means Rs 1,200. */
    FIXED_PER_WORKER
}
