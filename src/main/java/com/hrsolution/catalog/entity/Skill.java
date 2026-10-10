package com.hrsolution.catalog.entity;

import com.hrsolution.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A capability or certification a worker can hold - "PSARA Trained",
 * "ITI Electrical", "Heavy Vehicle Licence".
 *
 * <p>Attached to categories to describe what the role typically needs, and from
 * Phase 6 to individual workers to record what they actually have. The gap
 * between the two is what makes a deployment decision possible.
 */
@Entity
@Table(name = "skills")
@Getter
@Setter
public class Skill extends BaseEntity {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
