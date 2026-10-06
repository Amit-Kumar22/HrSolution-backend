package com.hrsolution.user.entity;

import com.hrsolution.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A single named capability, such as {@code PAYROLL_PROCESS}.
 *
 * <p>The catalogue is fixed by Flyway migration {@code V2} and is not editable
 * at runtime - a permission only means something if code checks for it, so
 * inventing one through an API would create an authority that grants nothing.
 * What <em>is</em> editable is which roles hold which permissions.
 *
 * <p>The name must match a constant in
 * {@code com.hrsolution.common.security.Permissions}; a test asserts the two
 * sets are identical.
 */
@Entity
@Table(name = "permissions")
@Getter
@Setter
public class Permission extends BaseEntity {

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    /** Grouping for the admin screen, e.g. {@code PAYROLL}, {@code SELF}. */
    @Column(name = "module", nullable = false, length = 40)
    private String module;

    @Column(name = "description", length = 255)
    private String description;
}
