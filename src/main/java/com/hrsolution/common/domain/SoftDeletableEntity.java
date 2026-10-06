package com.hrsolution.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Base class for business records that must never be hard-deleted - workers,
 * clients, payroll runs, invoices and friends.
 *
 * <p>Deleting means calling {@link #markDeleted(String)}; the row stays in the
 * table. Repositories for these entities must filter on {@code deleted = false}
 * in their queries, and their tables need the {@code deleted}, {@code deleted_at}
 * and {@code deleted_by} columns on top of the {@link BaseEntity} ones.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class SoftDeletableEntity extends BaseEntity {

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by", length = 150)
    private String deletedBy;

    public void markDeleted(String deletedByUser) {
        this.deleted = true;
        this.deletedAt = Instant.now();
        this.deletedBy = deletedByUser;
    }

    public void restore() {
        this.deleted = false;
        this.deletedAt = null;
        this.deletedBy = null;
    }
}
