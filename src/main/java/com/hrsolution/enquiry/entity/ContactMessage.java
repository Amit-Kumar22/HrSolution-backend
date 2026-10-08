package com.hrsolution.enquiry.entity;

import com.hrsolution.common.domain.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A general "Contact Us" submission.
 *
 * <p>Separate from {@link Enquiry} on purpose, though the forms look alike. An
 * enquiry is a sales lead with a pipeline, an owner and a requirement to quote
 * against; a contact message is correspondence — a question, a complaint, a job
 * applicant asking something. Merging them would mean either a sales pipeline
 * full of noise, or structured requirement fields left empty on most rows.
 */
@Entity
@Table(name = "contact_messages")
@Getter
@Setter
public class ContactMessage extends SoftDeletableEntity {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "email", nullable = false, length = 180)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "subject", length = 200)
    private String subject;

    @Column(name = "message", nullable = false, length = 2000)
    private String message;

    /**
     * Mapped to {@code read_flag}: {@code read} is a reserved word in several
     * SQL dialects and makes for queries that need quoting everywhere.
     */
    @Column(name = "read_flag", nullable = false)
    private boolean read = false;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "replied", nullable = false)
    private boolean replied = false;

    @Column(name = "spam", nullable = false)
    private boolean spam = false;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    public void markRead() {
        if (!read) {
            this.read = true;
            this.readAt = Instant.now();
        }
    }

    public void markUnread() {
        this.read = false;
        this.readAt = null;
    }
}
