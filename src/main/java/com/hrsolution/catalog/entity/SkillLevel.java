package com.hrsolution.catalog.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Skill classification for a manpower category.
 *
 * <p>Not a label. Indian minimum wage notifications are published per state
 * <em>and per skill level</em>, so this value selects which wage floor applies
 * when Phase 8 checks whether a worker is being paid legally. Changing a
 * category's level changes the minimum it may be paid.
 *
 * <p>{@link #ordinalRank} gives a stable ordering independent of the enum
 * declaration order, so persisted comparisons do not break if a level is ever
 * inserted in the middle.
 */
@Getter
@RequiredArgsConstructor
public enum SkillLevel {

    /** No formal training or certification required. Helpers, cleaners. */
    UNSKILLED("Unskilled", 1),

    /** Some training or a licence, but no trade qualification. Guards, drivers. */
    SEMI_SKILLED("Semi-skilled", 2),

    /** A trade qualification such as an ITI certificate. Electricians, welders. */
    SKILLED("Skilled", 3),

    /** Diploma or degree level, or substantial supervisory responsibility. */
    HIGHLY_SKILLED("Highly skilled", 4);

    private final String displayName;
    private final int ordinalRank;
}
