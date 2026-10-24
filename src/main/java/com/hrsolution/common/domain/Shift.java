package com.hrsolution.common.domain;

/**
 * Which shift a role is worked on.
 *
 * <p>Shared vocabulary rather than one module's enum: a job advertises a shift,
 * a requisition asks for one, and a deployment is worked on one. It lives in
 * common because all three need the same values to mean the same thing.
 *
 * <p>Matters beyond advertising copy: night work attracts a shift allowance in
 * most state minimum wage notifications, and rotational work changes how
 * attendance is counted. Phases 7 and 8 read it.
 */
public enum Shift {

    DAY,

    NIGHT,

    /** Rotates between day and night, typically weekly. */
    ROTATIONAL
}
