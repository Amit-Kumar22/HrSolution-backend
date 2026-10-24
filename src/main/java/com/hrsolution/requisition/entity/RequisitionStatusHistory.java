package com.hrsolution.requisition.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * One recorded move of a requisition through its lifecycle.
 *
 * <p>Deliberately not a {@code BaseEntity}, for the same reason as
 * {@code ApplicationStatusHistory}: a history row is written once and never
 * touched, so {@code updatedAt}, {@code version} and the soft-delete columns
 * would imply an editing capability this table must not have.
 *
 * <p>{@link #changedByEmail} duplicates what {@link #changedByUserId} could
 * reach, so the trail stays readable after a staff account is removed. A
 * requisition history that renders as "changed by user 47" two years later
 * settles no argument.
 */
@Entity
@Table(name = "requisition_status_history")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class RequisitionStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requisition_id", nullable = false)
    private Requisition requisition;

    /** Null on the row recording creation. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private RequisitionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private RequisitionStatus toStatus;

    @Column(name = "changed_by_user_id")
    private Long changedByUserId;

    @Column(name = "changed_by_email", length = 180)
    private String changedByEmail;

    @Column(name = "remarks", length = 1000)
    private String remarks;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isOrigin() {
        return fromStatus == null;
    }
}
