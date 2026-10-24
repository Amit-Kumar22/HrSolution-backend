package com.hrsolution.worker.entity;

import com.hrsolution.common.domain.SoftDeletableEntity;
import com.hrsolution.common.util.DateUtils;
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
 * A worker's monthly wage breakdown, as dated history.
 *
 * <p>Never updated in place. A revision inserts a new row and closes the
 * previous one the day before it starts - the same model as {@code RateCard} in
 * Phase 4, and for the same reason: recomputing March's payroll must use
 * March's structure, and an in-place edit would silently restate every payslip
 * already issued.
 *
 * <h2>Why the components are separate columns</h2>
 *
 * <p>One gross figure would be simpler and wrong. The statutory deductions each
 * have their own base:
 *
 * <ul>
 *   <li><strong>PF</strong> is computed on basic + DA, not gross.</li>
 *   <li><strong>ESI</strong> is on gross, but only below a wage ceiling.</li>
 *   <li><strong>HRA</strong> has its own exemption rules for income tax.</li>
 * </ul>
 *
 * <p>Phase 8's engine reads these individually, so collapsing them here would
 * make correct deductions impossible to compute later.
 */
@Entity
@Table(name = "salary_structures")
@Getter
@Setter
public class SalaryStructure extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @Column(name = "basic", nullable = false, precision = 12, scale = 2)
    private BigDecimal basic = MoneyUtils.ZERO;

    /** Dearness allowance. Part of the PF base, which is why it is separate. */
    @Column(name = "da", nullable = false, precision = 12, scale = 2)
    private BigDecimal da = MoneyUtils.ZERO;

    @Column(name = "hra", nullable = false, precision = 12, scale = 2)
    private BigDecimal hra = MoneyUtils.ZERO;

    @Column(name = "conveyance", nullable = false, precision = 12, scale = 2)
    private BigDecimal conveyance = MoneyUtils.ZERO;

    @Column(name = "other_allowance", nullable = false, precision = 12, scale = 2)
    private BigDecimal otherAllowance = MoneyUtils.ZERO;

    @Column(name = "ot_rate_per_hour", precision = 10, scale = 2)
    private BigDecimal otRatePerHour;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** Null means current. Closed by the successor's start date minus one day. */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "notes", length = 500)
    private String notes;

    // ------------------------------------------------------------------

    /** Total monthly gross. */
    public BigDecimal gross() {
        return MoneyUtils.scale(
                nz(basic).add(nz(da)).add(nz(hra)).add(nz(conveyance)).add(nz(otherAllowance)));
    }

    /**
     * The base PF is computed on: basic + DA.
     *
     * <p>Exposed here rather than left to Phase 8 so the rule lives with the
     * data it describes, and so a reviewer can see that HRA is correctly
     * excluded.
     */
    public BigDecimal providentFundBase() {
        return MoneyUtils.scale(nz(basic).add(nz(da)));
    }

    /** Whether this structure is the one in force on a date. */
    public boolean coversDate(LocalDate date) {
        return DateUtils.overlaps(effectiveFrom, effectiveTo, date, date);
    }

    /** Whether this is the current structure, i.e. not yet superseded. */
    public boolean isCurrent() {
        return effectiveTo == null;
    }

    /** Closes this structure the day before {@code successorStart}. */
    public void closeBefore(LocalDate successorStart) {
        this.effectiveTo = successorStart.minusDays(1);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? MoneyUtils.ZERO : value;
    }
}
