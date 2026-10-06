package com.hrsolution.settings.entity;

import com.hrsolution.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * The service provider's own company profile - one row, id {@code 1}.
 *
 * <p>Deliberately a database row rather than configuration properties, because
 * it is edited by a SUPER_ADMIN from the Settings screen without a redeploy, and
 * because almost everything the platform emits reads from it: the marketing site
 * header and footer, every outbound email, payslip headers and GST invoices.
 *
 * <p>{@link #stateCode} and {@link #gstin} carry real weight for billing: the
 * state code is compared against the client's to decide whether an invoice
 * attracts CGST+SGST (same state) or IGST (different state).
 */
@Entity
@Table(name = "company_settings")
@Getter
@Setter
public class CompanySettings extends BaseEntity {

    // ---------- Identity ----------

    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    /** Brand name, when it differs from the registered legal name. */
    @Column(name = "trade_name", length = 200)
    private String tradeName;

    @Column(name = "tagline", length = 300)
    private String tagline;

    @Column(name = "about", length = 2000)
    private String about;

    // ---------- Registered address ----------

    @Column(name = "address_line1", length = 200)
    private String addressLine1;

    @Column(name = "address_line2", length = 200)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    /** Two-digit GST state code, e.g. {@code 27} for Maharashtra. */
    @Column(name = "state_code", length = 2)
    private String stateCode;

    @Column(name = "pincode", length = 10)
    private String pincode;

    @Column(name = "country", length = 100)
    private String country;

    // ---------- Contact ----------

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "alternate_phone", length = 20)
    private String alternatePhone;

    @Column(name = "email", length = 180)
    private String email;

    @Column(name = "support_email", length = 180)
    private String supportEmail;

    @Column(name = "website", length = 200)
    private String website;

    // ---------- Statutory registrations ----------

    @Column(name = "gstin", length = 15)
    private String gstin;

    @Column(name = "pan", length = 10)
    private String pan;

    @Column(name = "tan", length = 10)
    private String tan;

    @Column(name = "cin", length = 21)
    private String cin;

    @Column(name = "pf_establishment_code", length = 30)
    private String pfEstablishmentCode;

    @Column(name = "esi_establishment_code", length = 30)
    private String esiEstablishmentCode;

    @Column(name = "pt_registration_number", length = 30)
    private String ptRegistrationNumber;

    // ---------- Bank details, printed on invoices ----------

    @Column(name = "bank_name", length = 120)
    private String bankName;

    @Column(name = "bank_branch", length = 120)
    private String bankBranch;

    @Column(name = "bank_account_number", length = 30)
    private String bankAccountNumber;

    @Column(name = "bank_ifsc", length = 11)
    private String bankIfsc;

    // ---------- Branding ----------

    /** Storage key for the logo, resolved by {@code StorageService} in Phase 3. */
    @Column(name = "logo_path", length = 400)
    private String logoPath;

    // ---------- Social links ----------

    @Column(name = "linkedin_url", length = 300)
    private String linkedinUrl;

    @Column(name = "facebook_url", length = 300)
    private String facebookUrl;

    @Column(name = "twitter_url", length = 300)
    private String twitterUrl;

    @Column(name = "instagram_url", length = 300)
    private String instagramUrl;
}
