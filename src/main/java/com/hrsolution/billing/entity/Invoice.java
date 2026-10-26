package com.hrsolution.billing.entity;

import com.hrsolution.client.entity.Client;
import com.hrsolution.common.domain.SoftDeletableEntity;
import com.hrsolution.payroll.entity.PayrollRun;
import com.hrsolution.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices")
@Getter
@Setter
public class Invoice extends SoftDeletableEntity {

    @Column(name = "invoice_number", nullable = false, unique = true, length = 30)
    private String invoiceNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_run_id")
    private PayrollRun payrollRun;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "place_of_supply", length = 10)
    private String placeOfSupply;

    @Column(name = "taxable_amount", precision = 14, scale = 2, nullable = false)
    private BigDecimal taxableAmount = BigDecimal.ZERO;

    @Column(name = "cgst_amount", precision = 14, scale = 2, nullable = false)
    private BigDecimal cgstAmount = BigDecimal.ZERO;

    @Column(name = "sgst_amount", precision = 14, scale = 2, nullable = false)
    private BigDecimal sgstAmount = BigDecimal.ZERO;

    @Column(name = "igst_amount", precision = 14, scale = 2, nullable = false)
    private BigDecimal igstAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", precision = 14, scale = 2, nullable = false)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "amount_paid", precision = 14, scale = 2, nullable = false)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(length = 1000)
    private String notes;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issued_by_id")
    private User issuedBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by_id")
    private User cancelledBy;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<InvoiceLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Payment> payments = new ArrayList<>();

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CreditNote> creditNotes = new ArrayList<>();

    public BigDecimal balanceDue() {
        BigDecimal balance = totalAmount.subtract(amountPaid);
        return balance.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : balance;
    }
}
