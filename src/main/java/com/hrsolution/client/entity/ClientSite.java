package com.hrsolution.client.entity;

import com.hrsolution.common.domain.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A physical location of a client where workers are deployed.
 *
 * <p>{@link #stateCode} is not a duplicate of the client's billing state. A
 * Pune-headquartered client can run a plant in Gujarat, and it is the
 * <em>site's</em> state that governs:
 *
 * <ul>
 *   <li>the minimum wage that applies to workers there (Phase 8),</li>
 *   <li>professional tax, which is levied per state (Phase 8),</li>
 *   <li>which Contract Labour (R&amp;A) Act licence covers the work (Phase 10).</li>
 * </ul>
 *
 * <p>Deploying workers to a site in the wrong state therefore produces wrong
 * wages, wrong deductions and a compliance gap - which is why this is a first
 * class field rather than free text on an address.
 */
@Entity
@Table(name = "client_sites")
@Getter
@Setter
public class ClientSite extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    /** Unique within the client, e.g. {@code PUNE-PLANT-1}. */
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;

    @Column(name = "site_name", nullable = false, length = 160)
    private String siteName;

    @Column(name = "address_line1", length = 200)
    private String addressLine1;

    @Column(name = "address_line2", length = 200)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    /** Governs minimum wage, professional tax and licensing for this site. */
    @Column(name = "state_code", length = 2)
    private String stateCode;

    @Column(name = "pincode", length = 10)
    private String pincode;

    // ---------- Who to call at the site ----------

    @Column(name = "site_incharge_name", length = 120)
    private String siteInchargeName;

    @Column(name = "site_incharge_phone", length = 20)
    private String siteInchargePhone;

    @Column(name = "site_incharge_email", length = 180)
    private String siteInchargeEmail;

    /** False hides it from new deployments while keeping existing records valid. */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** "PUNE-PLANT-1 — Pune Manufacturing Unit", for dropdowns. */
    public String displayLabel() {
        return "%s — %s".formatted(siteCode, siteName);
    }
}
