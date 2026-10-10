package com.hrsolution.client.entity;

import com.hrsolution.common.domain.SoftDeletableEntity;
import com.hrsolution.common.util.DateUtils;
import com.hrsolution.common.util.MoneyUtils;
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

/**
 * The commercial agreement with a client: what we charge on top of wages, and
 * for how long.
 *
 * <p>Phase 9 reads the contract in force for the billing month to compute the
 * service charge line on every invoice, which makes
 * {@link #serviceChargeFor(BigDecimal, int)} the commercially significant method
 * on this entity.
 */
@Entity
@Table(name = "client_contracts")
@Getter
@Setter
public class ClientContract extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    /** The client's or our own reference for the signed document. Unique. */
    @Column(name = "contract_number", nullable = false, length = 60)
    private String contractNumber;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Null means open-ended. Expiry reminders fire only on a set end date. */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_charge_type", nullable = false, length = 20)
    private ServiceChargeType serviceChargeType;

    /** A percentage when the type is PERCENTAGE, otherwise rupees per worker. */
    @Column(name = "service_charge_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal serviceChargeValue;

    /** Overrides {@code Client.paymentTermsDays} when set. */
    @Column(name = "payment_terms_days")
    private Integer paymentTermsDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ContractStatus status = ContractStatus.DRAFT;

    @Column(name = "terminated_on")
    private LocalDate terminatedOn;

    @Column(name = "termination_reason", length = 500)
    private String terminationReason;

    @Column(name = "notes", length = 2000)
    private String notes;

    // ------------------------------------------------------------------

    /**
     * The service charge for a billing period.
     *
     * <p>The whole reason {@link ServiceChargeType} is stored explicitly:
     *
     * <ul>
     *   <li>{@code PERCENTAGE} with value {@code 8.5} on a wage bill of
     *       1,00,000 gives 8,500.</li>
     *   <li>{@code FIXED_PER_WORKER} with value {@code 1200} and 25 workers
     *       gives 30,000, whatever the wages were.</li>
     * </ul>
     *
     * @param totalWages  the wage bill for the period
     * @param workerCount number of deployed workers billed for the period
     */
    public BigDecimal serviceChargeFor(BigDecimal totalWages, int workerCount) {
        if (serviceChargeValue == null) {
            return MoneyUtils.ZERO;
        }
        return switch (serviceChargeType) {
            case PERCENTAGE -> MoneyUtils.percentOf(totalWages, serviceChargeValue);
            case FIXED_PER_WORKER -> MoneyUtils.multiply(
                    serviceChargeValue, BigDecimal.valueOf(Math.max(0, workerCount)));
        };
    }

    /** Whether this contract covers {@code date}. Ignores status. */
    public boolean coversDate(LocalDate date) {
        if (date == null || startDate == null) {
            return false;
        }
        boolean startedByThen = !date.isBefore(startDate);
        boolean notYetEnded = endDate == null || !date.isAfter(endDate);
        return startedByThen && notYetEnded;
    }

    /** Whether this contract is ACTIVE and covers {@code date}. */
    public boolean isInForceOn(LocalDate date) {
        return status.isInForce() && coversDate(date);
    }

    /**
     * Whether this contract's dates overlap another's.
     *
     * <p>Uses the shared {@code DateUtils.overlaps}, which treats a null end
     * date as open-ended - so an open-ended contract conflicts with anything
     * starting after it.
     */
    public boolean overlaps(ClientContract other) {
        return DateUtils.overlaps(startDate, endDate, other.getStartDate(), other.getEndDate());
    }

    /** Days until expiry, or null when open-ended or already ended. */
    public Long daysUntilExpiry(LocalDate asOf) {
        if (endDate == null || asOf == null || endDate.isBefore(asOf)) {
            return null;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(asOf, endDate);
    }

    /** The credit period that applies: the contract's, else the client's. */
    public int effectivePaymentTermsDays() {
        if (paymentTermsDays != null) {
            return paymentTermsDays;
        }
        return client == null ? 30 : client.getPaymentTermsDays();
    }
}
