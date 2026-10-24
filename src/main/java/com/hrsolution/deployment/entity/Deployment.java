package com.hrsolution.deployment.entity;

import com.hrsolution.catalog.entity.ManpowerCategory;
import com.hrsolution.client.entity.Client;
import com.hrsolution.client.entity.ClientSite;
import com.hrsolution.common.domain.Shift;
import com.hrsolution.common.domain.SoftDeletableEntity;
import com.hrsolution.common.util.DateUtils;
import com.hrsolution.common.util.MoneyUtils;
import com.hrsolution.requisition.entity.Requisition;
import com.hrsolution.worker.entity.Worker;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * A worker posted to a client site for a period.
 *
 * <h2>The invariant that matters</h2>
 *
 * <p><strong>A worker must never hold two overlapping active deployments.</strong>
 * A person cannot be at two client sites at once, and two overlapping rows
 * would bill both clients for the same body and double-count the same
 * attendance in Phase 7. {@link #overlaps} implements the test;
 * {@code DeploymentService} applies it before every insert. MySQL cannot
 * express it as a constraint, so it lives in code and is tested directly.
 *
 * <h2>Why the rates are snapshotted</h2>
 *
 * <p>{@link #monthlyWage} and {@link #billingRate} are copied from the rate
 * card applicable on {@link #startDate}, not read through to it. A rate card
 * revision must not silently restate what an existing deployment costs - Phase
 * 9 invoices from these numbers, and an invoice that changes after it was
 * issued is a dispute. The same reasoning as the rate snapshot on a contract.
 */
@Entity
@Table(name = "deployments")
@Getter
@Setter
public class Deployment extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private ClientSite site;

    /** The demand this fulfils. Null for a direct posting with no requisition. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requisition_id")
    private Requisition requisition;

    /** The role the worker is posted as, which may differ from their usual one. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ManpowerCategory category;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Null means currently deployed; the overlap test treats it as open-ended. */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "shift", length = 20)
    private Shift shift;

    /** Snapshotted from the rate card - see the class note. */
    @Column(name = "monthly_wage", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyWage = MoneyUtils.ZERO;

    /** Snapshotted from the rate card - see the class note. */
    @Column(name = "billing_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal billingRate = MoneyUtils.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DeploymentStatus status = DeploymentStatus.ACTIVE;

    @Column(name = "released_on")
    private LocalDate releasedOn;

    @Column(name = "release_reason", length = 500)
    private String releaseReason;

    @Column(name = "notes", length = 2000)
    private String notes;

    // ------------------------------------------------------------------
    // Domain behaviour
    // ------------------------------------------------------------------

    /** Whether this posting's dates cover a date, status aside. */
    public boolean coversDate(LocalDate date) {
        if (date == null || startDate == null) {
            return false;
        }
        return !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate));
    }

    /** Whether this posting is active and covers a date. */
    public boolean isInForceOn(LocalDate date) {
        return status.isActive() && coversDate(date);
    }

    /**
     * Whether this posting's dates overlap another's.
     *
     * <p>Uses the shared {@code DateUtils.overlaps}, which treats a null end
     * date as open-ended - so an open-ended posting conflicts with anything
     * starting after it, which is exactly right: a worker with no release date
     * is not available.
     */
    public boolean overlaps(Deployment other) {
        if (other == null) {
            return false;
        }
        return DateUtils.overlaps(startDate, endDate, other.getStartDate(), other.getEndDate());
    }

    /** Whether this posting's dates overlap an arbitrary period. */
    public boolean overlapsPeriod(LocalDate from, LocalDate to) {
        return DateUtils.overlaps(startDate, endDate, from, to);
    }

    /**
     * Gross margin per month on this posting.
     *
     * <p>Before employer PF and ESI contributions and before the contract's
     * service charge, so it is an indicator rather than a profit figure. Phase 9
     * computes the real one.
     */
    public BigDecimal grossMarginPerMonth() {
        if (monthlyWage == null || billingRate == null) {
            return null;
        }
        return MoneyUtils.scale(billingRate.subtract(monthlyWage));
    }

    /** True when the client is billed less than the worker is paid. */
    public boolean isBillingBelowWage() {
        return monthlyWage != null && billingRate != null
                && billingRate.compareTo(monthlyWage) < 0;
    }

    /** Days served up to a date, inclusive of the start. */
    public long daysServedOn(LocalDate asOf) {
        if (startDate == null || asOf == null || asOf.isBefore(startDate)) {
            return 0;
        }
        LocalDate until = endDate != null && endDate.isBefore(asOf) ? endDate : asOf;
        return ChronoUnit.DAYS.between(startDate, until) + 1;
    }

    /**
     * Ends this posting.
     *
     * <p>Sets the end date as well as the status, because a posting with a
     * terminal status and no end date would still read as open-ended to
     * {@link #overlaps} - and would therefore keep blocking the worker from
     * being posted elsewhere.
     */
    public void end(DeploymentStatus endStatus, LocalDate on, String reason) {
        this.status = endStatus;
        this.releasedOn = on;
        this.releaseReason = reason;
        if (endDate == null || endDate.isAfter(on)) {
            this.endDate = on;
        }
    }

    /** "Bharat Textiles / PUNE-PLANT-1 — Security Guard", for a worker's history. */
    public String displayLabel() {
        String clientName = client == null ? "?" : client.displayName();
        String siteCode = site == null ? "?" : site.getSiteCode();
        String role = category == null ? "?" : category.getName();
        return "%s / %s — %s".formatted(clientName, siteCode, role);
    }
}
