package com.hrsolution.catalog.entity;

import com.hrsolution.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A kind of worker this company supplies: Security Guard, Electrician, Helper.
 *
 * <p>Central master data, referenced from Phase 3 onwards by enquiries, and
 * later by requisitions, rate cards, workers and payroll. That reach is why
 * {@link #code} exists alongside {@link #name}: the display name can be
 * reworded freely, while the code is the stable identifier used in Excel
 * imports, exports and any future integration.
 *
 * <p>Not soft-deletable, but {@link #active} serves the same purpose — a
 * category that is no longer offered is deactivated so it disappears from
 * dropdowns while the historical records that reference it stay intact.
 */
@Entity
@Table(name = "manpower_categories")
@Getter
@Setter
public class ManpowerCategory extends BaseEntity {

    /** Stable machine identifier, e.g. {@code SECURITY_GUARD}. Immutable in practice. */
    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    /** Selects the applicable minimum wage band. See {@link SkillLevel}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "skill_level", nullable = false, length = 20)
    private SkillLevel skillLevel;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    /** False hides it from new enquiries and requisitions, keeping history valid. */
    @Column(name = "active", nullable = false)
    private boolean active = true;
}
