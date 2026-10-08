package com.hrsolution.enquiry.entity;

import com.hrsolution.catalog.entity.ManpowerCategory;
import com.hrsolution.common.domain.SoftDeletableEntity;
import com.hrsolution.user.entity.User;
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
 * A manpower requirement submitted through the public website.
 *
 * <p>This is where the sales pipeline begins, which shapes two decisions:
 *
 * <p><strong>Almost everything is nullable.</strong> Only the company name,
 * contact person and phone are required. A form that rejects a lead because the
 * visitor did not know their start date is a form that loses business — collect
 * what they offer and chase the rest by phone.
 *
 * <p><strong>Soft-deletable.</strong> An enquiry is a business record; removing
 * one would erase the origin of a client relationship.
 */
@Entity
@Table(name = "enquiries")
@Getter
@Setter
public class Enquiry extends SoftDeletableEntity {

    // ---------- Who is asking ----------

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "contact_person", nullable = false, length = 120)
    private String contactPerson;

    /** Required: for an Indian B2B enquiry the phone is the real channel. */
    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "email", length = 180)
    private String email;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    // ---------- What they need ----------

    /**
     * The category picked from the dropdown, if any.
     *
     * <p>Lazy, and the admin list query fetches it explicitly — the enquiry
     * list is the most-read screen in this module and would otherwise fire a
     * query per row.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ManpowerCategory category;

    /**
     * Free text for when the enquirer picks "Other", or types something the
     * catalogue does not cover. Both this and {@link #category} may be null:
     * the form never blocks a submission over a taxonomy gap.
     */
    @Column(name = "other_category", length = 120)
    private String otherCategory;

    @Column(name = "number_of_workers")
    private Integer numberOfWorkers;

    @Column(name = "duration_months")
    private Integer durationMonths;

    @Column(name = "required_from")
    private LocalDate requiredFrom;

    @Column(name = "message", length = 2000)
    private String message;

    // ---------- How we are handling it ----------

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EnquiryStatus status = EnquiryStatus.NEW;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_user_id")
    private User assignedTo;

    /** Staff-only. Never returned by a public endpoint. */
    @Column(name = "internal_notes", length = 2000)
    private String internalNotes;

    // ---------- Provenance, for abuse investigation ----------

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    // ------------------------------------------------------------------

    /** What to show as the requirement: the category name, or the free text. */
    public String categoryLabel() {
        if (category != null) {
            return category.getName();
        }
        return otherCategory == null || otherCategory.isBlank() ? "Not specified" : otherCategory;
    }

    /** Appends a timestamped note rather than overwriting what is there. */
    public void appendInternalNote(String note, String author) {
        if (note == null || note.isBlank()) {
            return;
        }
        String entry = "[%s by %s] %s".formatted(java.time.Instant.now(), author, note.trim());
        this.internalNotes = internalNotes == null || internalNotes.isBlank()
                ? entry
                : internalNotes + "\n" + entry;

        // The column holds 2000 characters; keep the most recent notes.
        if (this.internalNotes.length() > 2000) {
            this.internalNotes = this.internalNotes.substring(this.internalNotes.length() - 2000);
        }
    }
}
