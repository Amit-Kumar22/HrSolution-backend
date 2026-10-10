package com.hrsolution.client.entity;

import com.hrsolution.catalog.entity.ManpowerCategory;
import com.hrsolution.common.domain.SoftDeletableEntity;
import com.hrsolution.common.util.MoneyUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The agreed wage and billing rate for one category of worker at one client.
 *
 * <h2>Dated history, not current values</h2>
 *
 * <p>Rate cards are <strong>never edited in place once used</strong>. A rate
 * change creates a successor row and sets {@link #effectiveTo} on the
 * predecessor, because:
 *
 * <ul>
 *   <li>recomputing March's payroll must use March's wage, not today's;</li>
 *   <li>reissuing or crediting an old invoice must use the rate that was
 *       billed;</li>
 *   <li>a wage dispute is settled by what the rate card said at the time.</li>
 * </ul>
 *
 * <p>Editing in place would silently rewrite history in all three cases.
 *
 * <h2>Site-specific overrides</h2>
 *
 * <p>A null {@link #site} means the rate applies to every site of the client. A
 * row naming a site wins over a client-wide row for the same category, which
 * matters because minimum wages differ by state and a client's plants may sit
 * in different ones.
 *
 * <h2>On showing the wage to the client</h2>
 *
 * <p>Both {@link #monthlyWage} (what the worker is paid) and
 * {@link #billingRate} (what the client pays) are visible to the client's own
 * users, which exposes the margin. That is deliberate: under the Contract
 * Labour (Regulation and Abolition) Act the principal employer is liable for
 * ensuring contract workers actually receive at least the minimum wage, so a
 * client that cannot see the wage cannot discharge its own obligation. The
 * margin is arithmetic they could do anyway.
 */
@Entity
@Table(name = "rate_cards")
@Getter
@Setter
public class RateCard extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ManpowerCategory category;

    /** Null means every site of this client. A named site takes precedence. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id")
    private ClientSite site;

    /**
     * Monthly wage payable to the worker. Checked against the state minimum
     * wage for the site and the category's skill level in Phase 8.
     */
    @Column(name = "monthly_wage", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyWage;

    /**
     * Monthly amount billed to the client per worker, before employer PF and
     * ESI, service charge and GST - those are separate invoice lines.
     */
    @Column(name = "billing_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal billingRate;

    @Column(name = "ot_rate_per_hour", precision = 10, scale = 2)
    private BigDecimal otRatePerHour;

    @Column(name = "shift_hours", nullable = false)
    private int shiftHours = 8;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** Null means current. Closed off when a successor is created. */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "notes", length = 500)
    private String notes;

    // ------------------------------------------------------------------

    /** Whether this row is the applicable one on {@code date}. */
    public boolean isEffectiveOn(LocalDate date) {
        if (date == null || effectiveFrom == null) {
            return false;
        }
        boolean started = !date.isBefore(effectiveFrom);
        boolean notEnded = effectiveTo == null || !date.isAfter(effectiveTo);
        return started && notEnded;
    }

    /** True while this is the open, current row. */
    public boolean isCurrent() {
        return effectiveTo == null;
    }

    /** True when the rate is tied to a single site rather than the whole client. */
    public boolean isSiteSpecific() {
        return site != null;
    }

    /**
     * Gross margin per worker per month, before employer contributions and the
     * contract service charge. Exposed because the client can compute it
     * anyway, and because a negative value is a data-entry error worth seeing.
     */
    public BigDecimal grossMarginPerWorker() {
        return MoneyUtils.subtract(billingRate, monthlyWage);
    }

    /** True when we would bill less than we pay - almost certainly a mistake. */
    public boolean isBillingBelowWage() {
        return MoneyUtils.isNegative(grossMarginPerWorker());
    }

    /** Closes this row off the day before its successor takes effect. */
    public void closeBefore(LocalDate successorEffectiveFrom) {
        this.effectiveTo = successorEffectiveFrom.minusDays(1);
    }
}
