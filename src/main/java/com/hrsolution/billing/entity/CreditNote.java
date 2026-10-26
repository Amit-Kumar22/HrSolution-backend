package com.hrsolution.billing.entity;

import com.hrsolution.common.domain.BaseEntity;
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

@Entity
@Table(name = "credit_notes")
@Getter
@Setter
public class CreditNote extends BaseEntity {

    @Column(name = "credit_note_number", nullable = false, unique = true, length = 30)
    private String creditNoteNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 500)
    private String reason;
}
