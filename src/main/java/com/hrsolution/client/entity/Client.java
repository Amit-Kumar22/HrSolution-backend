package com.hrsolution.client.entity;

import com.hrsolution.common.domain.SoftDeletableEntity;
import com.hrsolution.content.entity.Industry;
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

import java.time.LocalDate;

/**
 * A client company that buys manpower from us.
 *
 * <p>The commercial root of the system: sites, contracts, rate cards,
 * requisitions, deployments and invoices all hang off a client.
 *
 * <p>{@link #billingStateCode} deserves particular care. It is the single input
 * that decides whether an invoice carries CGST+SGST or IGST, so a wrong value
 * files tax under the wrong heads - a correction with the GST department rather
 * than a corrected invoice. See {@link #gstTreatmentAgainst(String)}.
 *
 * <p>Soft-deletable, and never hard-deleted: invoices, payroll runs and
 * statutory returns reference clients for years afterwards.
 */
@Entity
@Table(name = "clients")
@Getter
@Setter
public class Client extends SoftDeletableEntity {

    // ---------- Identity ----------

    /** As registered. This is the name that appears on invoices. */
    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    /** The name people actually use, when it differs. */
    @Column(name = "trade_name", length = 200)
    private String tradeName;

    /**
     * Nullable, deliberately. A client is often onboarded from an approved
     * registration before the paperwork arrives, and refusing the whole record
     * over a missing GSTIN would stop work from starting. Phase 9 refuses to
     * issue an invoice without one, which is the right place for that gate.
     */
    @Column(name = "gstin", length = 15)
    private String gstin;

    @Column(name = "pan", length = 10)
    private String pan;

    @Column(name = "cin", length = 21)
    private String cin;

    // ---------- Billing address ----------
    // Distinct from any site address: work happens at sites, invoices come here.

    @Column(name = "billing_address_line1", length = 200)
    private String billingAddressLine1;

    @Column(name = "billing_address_line2", length = 200)
    private String billingAddressLine2;

    @Column(name = "billing_city", length = 100)
    private String billingCity;

    @Column(name = "billing_state", length = 100)
    private String billingState;

    /** Two-digit GST state code. Decides CGST+SGST vs IGST. */
    @Column(name = "billing_state_code", length = 2)
    private String billingStateCode;

    @Column(name = "billing_pincode", length = 10)
    private String billingPincode;

    @Column(name = "billing_country", length = 100)
    private String billingCountry = "India";

    // ---------- Classification and state ----------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "industry_id")
    private Industry industry;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ClientStatus status = ClientStatus.PENDING_APPROVAL;

    /**
     * Default credit period in days. An active contract may override it; this
     * is the fallback when none does.
     */
    @Column(name = "payment_terms_days", nullable = false)
    private int paymentTermsDays = 30;

    @Column(name = "onboarded_on")
    private LocalDate onboardedOn;

    @Column(name = "notes", length = 2000)
    private String notes;

    // ------------------------------------------------------------------

    /**
     * A stable, human-quotable code such as {@code CLI-00042}.
     *
     * <p>Derived from the id rather than stored. It carries no information the
     * id does not, it can never drift out of step, and generating a stored code
     * would need either a second write or a counter table with its own
     * contention. Invoice numbers are different - those are a legally mandated
     * series that must never be reused, and Phase 9 gives them a real table.
     */
    public String clientCode() {
        return getId() == null ? null : "CLI-%05d".formatted(getId());
    }

    /** Trade name when set, otherwise the legal name. For lists and dropdowns. */
    public String displayName() {
        return tradeName == null || tradeName.isBlank() ? legalName : tradeName;
    }

    /**
     * Whether an invoice to this client attracts CGST+SGST or IGST.
     *
     * @param companyStateCode the service provider's own GST state code, from
     *                         company settings
     */
    public GstTreatment gstTreatmentAgainst(String companyStateCode) {
        return GstTreatment.resolve(companyStateCode, billingStateCode);
    }

    /** Whether new requisitions, deployments and contracts are permitted. */
    public boolean allowsNewWork() {
        return !isDeleted() && status.allowsNewWork();
    }
}
