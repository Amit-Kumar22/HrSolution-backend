package com.hrsolution.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Base class for every persistent entity.
 *
 * <p>Gives all entities a surrogate {@code BIGINT} id, the four audit columns
 * required by the project conventions, and an optimistic-locking version.
 * The audit columns are populated by Spring Data JPA auditing - see
 * {@link AuditorAwareImpl} for where {@code createdBy}/{@code updatedBy} come from.
 *
 * <p>Every concrete entity table must therefore contain: {@code id},
 * {@code created_at}, {@code updated_at}, {@code created_by}, {@code updated_by},
 * {@code version}. The Flyway migration and the entity are cross-checked at
 * startup by {@code spring.jpa.hibernate.ddl-auto=validate}.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", length = 150, updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 150)
    private String updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** True until the entity has been persisted and assigned an id. */
    public boolean isNew() {
        return id == null;
    }

    /**
     * Identity is the database id. Two entities of the same type are equal only
     * when both are persisted and share an id; unsaved entities are equal only to
     * themselves. {@link Hibernate#getClass} is used instead of {@code getClass()}
     * so a lazy proxy compares equal to its initialised counterpart.
     */
    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (!Hibernate.getClass(this).equals(Hibernate.getClass(other))) {
            return false;
        }
        Long thisId = getId();
        Long otherId = ((BaseEntity) other).getId();
        return thisId != null && thisId.equals(otherId);
    }

    /**
     * Constant per type. Deliberately not based on the id: the id is null before
     * the first flush, and an entity must not change its hash code mid-session
     * or it gets lost inside any {@code HashSet} that already holds it.
     */
    @Override
    public final int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
