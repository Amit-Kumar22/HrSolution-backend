package com.hrsolution.requisition.entity;

import com.hrsolution.catalog.entity.ManpowerCategory;
import com.hrsolution.catalog.entity.SkillLevel;
import com.hrsolution.client.entity.Client;
import com.hrsolution.client.entity.ClientSite;
import com.hrsolution.common.domain.Shift;
import com.hrsolution.common.domain.SoftDeletableEntity;
import com.hrsolution.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A client's request for workers: N of a category, at a site, from a date.
 *
 * <h2>Why the site is mandatory</h2>
 *
 * <p>{@link #site} is {@code NOT NULL}, unlike the optional site on a job
 * advert. A requisition has to be costable: the site's state code selects the
 * minimum wage, the professional tax slab and the rate card to resolve. Without
 * it the record is a requirement nobody can act on, and storing one would push
 * the problem to whoever tries to deploy against it.
 *
 * <h2>Fulfilment is counted, not stored</h2>
 *
 * <p>There is no {@code quantityDeployed} column. The number comes from the
 * deployments table on read, the same decision as job openings in Phase 5: a
 * stored counter drifts the moment a deployment is released, and the drift is
 * invisible until someone reconciles a headcount by hand.
 *
 * <p>{@link #statusHistory} is append-only and written by {@link #moveTo}, so
 * there is no path that changes the status without recording who did it.
 */
@Entity
@Table(name = "requisitions")
@Getter
@Setter
public class Requisition extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    /** Mandatory - see the class note. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private ClientSite site;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ManpowerCategory category;

    @Column(name = "quantity_required", nullable = false)
    private int quantityRequired = 1;

    /** Overrides the category's default grade when the client asks for one. */
    @Enumerated(EnumType.STRING)
    @Column(name = "skill_level", length = 20)
    private SkillLevel skillLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "shift", length = 20)
    private Shift shift;

    @Column(name = "required_from", nullable = false)
    private LocalDate requiredFrom;

    /** Null means ongoing. */
    @Column(name = "required_to")
    private LocalDate requiredTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RequisitionStatus status = RequisitionStatus.DRAFT;

    /** May be a client portal user, or staff raising it after a phone call. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raised_by_user_id")
    private User raisedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "notes", length = 2000)
    private String notes;

    @OneToMany(mappedBy = "requisition", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC, id ASC")
    private List<RequisitionStatusHistory> statusHistory = new ArrayList<>();

    // ------------------------------------------------------------------
    // Domain behaviour
    // ------------------------------------------------------------------

    /** {@code REQ-000042}. Derived from the id, like every other code here. */
    public String requisitionNumber() {
        return getId() == null ? null : "REQ-%06d".formatted(getId());
    }

    /** Records creation as the first history row, with a null from-status. */
    public void recordCreation(Long actorUserId, String actorEmail, String remarks) {
        appendHistory(null, status, actorUserId, actorEmail, remarks);
    }

    /**
     * Moves to {@code target}, appending to the trail.
     *
     * @return true when the status changed
     * @throws IllegalStateException when the move is not allowed. The service
     *         translates this; the entity does not depend on the web types.
     */
    public boolean moveTo(RequisitionStatus target, Long actorUserId, String actorEmail,
                          String remarks) {
        if (target == null) {
            throw new IllegalStateException("A target status is required.");
        }
        if (target == status) {
            return false;
        }
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "A requisition cannot move from %s to %s.".formatted(status, target));
        }

        RequisitionStatus previous = status;
        this.status = target;

        if (target != RequisitionStatus.REJECTED) {
            this.rejectionReason = null;
        }

        appendHistory(previous, target, actorUserId, actorEmail, remarks);
        return true;
    }

    private void appendHistory(RequisitionStatus from, RequisitionStatus to,
                               Long actorUserId, String actorEmail, String remarks) {
        RequisitionStatusHistory entry = new RequisitionStatusHistory();
        entry.setRequisition(this);
        entry.setFromStatus(from);
        entry.setToStatus(to);
        entry.setChangedByUserId(actorUserId);
        entry.setChangedByEmail(actorEmail);
        entry.setRemarks(remarks);
        statusHistory.add(entry);
    }

    /** Whether this requirement covers a date. */
    public boolean coversDate(LocalDate date) {
        if (date == null || requiredFrom == null) {
            return false;
        }
        return !date.isBefore(requiredFrom) && (requiredTo == null || !date.isAfter(requiredTo));
    }

    /**
     * The status implied by how many workers are deployed.
     *
     * <p>Returned rather than applied, so {@code RequisitionService} decides
     * whether the move is legal and records it through {@link #moveTo}. A
     * requisition that has gone terminal keeps its status whatever the count
     * says - a cancelled requirement with two people still winding down must
     * not flip itself back to partially fulfilled.
     */
    public RequisitionStatus fulfilmentStatusFor(long deployedCount) {
        if (status.isTerminal() || !status.isCommitted()) {
            return status;
        }
        if (deployedCount <= 0) {
            return RequisitionStatus.IN_PROGRESS;
        }
        return deployedCount >= quantityRequired
                ? RequisitionStatus.FULFILLED
                : RequisitionStatus.PARTIALLY_FULFILLED;
    }

    /** How many more workers are wanted, floored at zero. */
    public long outstandingFor(long deployedCount) {
        return Math.max(0, quantityRequired - deployedCount);
    }

    /** Whether the requirement starts within {@code days} and is not yet met. */
    public boolean isUrgentOn(LocalDate asOf, int days, long deployedCount) {
        if (requiredFrom == null || asOf == null || outstandingFor(deployedCount) == 0) {
            return false;
        }
        return !requiredFrom.isAfter(asOf.plusDays(days));
    }

    /** The grade actually wanted: the explicit override, else the category's. */
    public SkillLevel effectiveSkillLevel() {
        if (skillLevel != null) {
            return skillLevel;
        }
        return category == null ? null : category.getSkillLevel();
    }
}
