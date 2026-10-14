package com.hrsolution.recruitment.entity;

/**
 * Which shift a role is worked on.
 *
 * <p>Matters beyond advertising copy: night work attracts a shift allowance in
 * most state minimum wage notifications, and rotational work changes how
 * attendance is counted. Phases 7 and 8 read it; here it is just advertised.
 */
public enum Shift {

    DAY,

    NIGHT,

    /** Rotates between day and night, typically weekly. */
    ROTATIONAL
}
