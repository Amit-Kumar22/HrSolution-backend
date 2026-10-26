package com.hrsolution.compliance.entity;

import com.hrsolution.client.entity.Client;
import com.hrsolution.common.domain.SoftDeletableEntity;
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

@Entity
@Table(name = "licences")
@Getter
@Setter
public class Licence extends SoftDeletableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "licence_type", nullable = false, length = 30)
    private LicenceType licenceType;

    @Column(name = "licence_number", nullable = false, length = 100)
    private String licenceNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "document_path", length = 500)
    private String documentPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LicenceStatus status = LicenceStatus.ACTIVE;

    @Column(name = "issuing_authority", length = 200)
    private String issuingAuthority;

    @Column(length = 1000)
    private String notes;

    public long daysUntilExpiry() {
        if (expiryDate == null) {
            return Long.MAX_VALUE;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
    }

    public boolean isExpiringSoon(int withinDays) {
        long days = daysUntilExpiry();
        return days >= 0 && days <= withinDays;
    }
}
