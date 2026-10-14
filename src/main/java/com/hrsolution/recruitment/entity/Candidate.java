package com.hrsolution.recruitment.entity;

import com.hrsolution.catalog.entity.ManpowerCategory;
import com.hrsolution.common.domain.SoftDeletableEntity;
import com.hrsolution.document.entity.StoredDocument;
import com.hrsolution.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A person in the hiring pipeline, with or without a login.
 *
 * <h2>Email is the identity key</h2>
 *
 * <p>{@link #email} is unique across the table, and that is the whole design.
 * Someone who applies to four jobs over two years must be one candidate with
 * four applications, not four candidates - otherwise the recruiter cannot see
 * that this person was interviewed and rejected last year, which is the single
 * most useful thing the system can tell them.
 *
 * <h2>Why a candidate may have no user</h2>
 *
 * <p>{@link #user} is null for an anonymous website application. Requiring
 * registration before applying costs real applicants, especially for operative
 * roles filled from a phone. The account is created later if the person wants
 * to track their application, and {@code CandidateService} links it to this row
 * by email at that point.
 *
 * <p>The consequence is that an anonymous submission can arrive quoting an
 * email that already belongs to a registered candidate. It is reused rather
 * than duplicated, but <strong>it never overwrites identity fields</strong> -
 * see {@code CandidateService.resolveForApplication}. Otherwise anyone who
 * knows an address could rewrite that person's name, phone and CV.
 *
 * <h2>Consent</h2>
 *
 * <p>{@link #consentGiven} is the DPDP Act basis for holding this record at
 * all. A CV is personal data processed for a declared purpose, and the column
 * is {@code NOT NULL} with no database default precisely so that every write
 * path has to state it rather than inherit a convenient {@code true}.
 */
@Entity
@Table(name = "candidates")
@Getter
@Setter
public class Candidate extends SoftDeletableEntity {

    /** The CANDIDATE-role login, once there is one. Null for guest applications. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    /** Unique. The identity key for the pipeline - see the class note. */
    @Column(name = "email", nullable = false, length = 180)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "total_experience_months")
    private Integer totalExperienceMonths;

    @Column(name = "highest_qualification", length = 120)
    private String highestQualification;

    /** What they want to do, for shortlisting against open jobs. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_category_id")
    private ManpowerCategory preferredCategory;

    /**
     * The person's current CV.
     *
     * <p>Distinct from {@code Application.resumeDocument}, which is the file
     * actually submitted with a given application. This one moves as they
     * update it; that one does not.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_document_id")
    private StoredDocument resumeDocument;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private CandidateSource source = CandidateSource.WEBSITE;

    @Column(name = "consent_given", nullable = false)
    private boolean consentGiven;

    @Column(name = "consent_given_at")
    private Instant consentGivenAt;

    /** Set when a submission trips the honeypot or timing checks. Stored, not rejected. */
    @Column(name = "spam_suspected", nullable = false)
    private boolean spamSuspected = false;

    @Column(name = "spam_reason", length = 200)
    private String spamReason;

    /** Internal. Never returned to the candidate's own portal. */
    @Column(name = "notes", length = 2000)
    private String notes;

    // ------------------------------------------------------------------
    // Domain behaviour
    // ------------------------------------------------------------------

    /** Whether this candidate can sign in and track their own applications. */
    public boolean hasLogin() {
        return user != null;
    }

    public boolean hasResume() {
        return resumeDocument != null;
    }

    /** Records consent, stamping the time only on the transition to true. */
    public void giveConsent() {
        if (!consentGiven) {
            this.consentGiven = true;
            this.consentGivenAt = Instant.now();
        }
    }

    public void flagAsSpam(String reason) {
        this.spamSuspected = true;
        this.spamReason = reason;
    }

    public void clearSpamFlag() {
        this.spamSuspected = false;
        this.spamReason = null;
    }

    /** Total experience as "3 years 6 months", or null when not stated. */
    public String experienceLabel() {
        if (totalExperienceMonths == null) {
            return null;
        }
        if (totalExperienceMonths == 0) {
            return "Fresher";
        }
        int wholeYears = totalExperienceMonths / 12;
        int months = totalExperienceMonths % 12;
        if (wholeYears == 0) {
            return months == 1 ? "1 month" : months + " months";
        }
        String yearPart = wholeYears == 1 ? "1 year" : wholeYears + " years";
        return months == 0 ? yearPart : yearPart + " " + months + " months";
    }

    /**
     * Masks the email for display in a context where the full address should
     * not be shown, e.g. {@code a***a@example.com}.
     *
     * <p>Not used by the staff console, which legitimately sees the address. It
     * exists for the duplicate-application error returned to an anonymous
     * caller: telling them "you already applied" has to be possible without
     * confirming the address to whoever typed it.
     */
    public String maskedEmail() {
        return maskEmail(email);
    }

    /** Static so callers can mask an address they have not loaded a candidate for. */
    public static String maskEmail(String address) {
        if (address == null || !address.contains("@")) {
            return null;
        }
        int at = address.indexOf('@');
        String local = address.substring(0, at);
        String domain = address.substring(at);
        if (local.length() <= 2) {
            return "*".repeat(local.length()) + domain;
        }
        return local.charAt(0) + "*".repeat(local.length() - 2) + local.charAt(local.length() - 1)
                + domain;
    }
}
