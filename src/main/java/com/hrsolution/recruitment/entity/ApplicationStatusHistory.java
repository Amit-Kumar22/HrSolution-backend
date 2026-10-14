package com.hrsolution.recruitment.entity;

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
 * One recorded move of an application through the funnel.
 *
 * <p>Deliberately <strong>not</strong> a {@code BaseEntity}. A history row is
 * written once and never touched again, so {@code updatedAt}, {@code version}
 * and the soft-delete columns would all be dead weight that implied an editing
 * capability this table must not have. It carries {@code createdAt} and nothing
 * else from the auditing set.
 *
 * <p>{@link #changedByEmail} duplicates what could be read through
 * {@link #changedByUserId}. That is intentional: the trail has to stay readable
 * after a staff account is removed, and a funnel history that renders as
 * "changed by user 47" a year later is not evidence of anything.
 *
 * <p>{@link #changedByUserId} is null when the candidate acted on their own
 * application - a public submission or a self-service withdrawal. Null there
 * means "not a member of staff", which is a meaningful distinction when
 * auditing who moved what.
 */
@Entity
@Table(name = "application_status_history")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class ApplicationStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    /** Null on the origin row, which records the application being created. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private ApplicationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private ApplicationStatus toStatus;

    /** Null when the candidate acted on their own application. */
    @Column(name = "changed_by_user_id")
    private Long changedByUserId;

    /** Denormalised so the trail survives the user record going away. */
    @Column(name = "changed_by_email", length = 180)
    private String changedByEmail;

    @Column(name = "notes", length = 1000)
    private String notes;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Whether this row records the application being submitted. */
    public boolean isOrigin() {
        return fromStatus == null;
    }

    /** Whether a member of staff made this move, as opposed to the candidate. */
    public boolean madeByStaff() {
        return changedByUserId != null;
    }
}
