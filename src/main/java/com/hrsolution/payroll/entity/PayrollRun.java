package com.hrsolution.payroll.entity;

import com.hrsolution.client.entity.Client;
import com.hrsolution.common.domain.SoftDeletableEntity;
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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "payroll_runs",
        uniqueConstraints = @UniqueConstraint(name = "uk_pr_client_month_year", columnNames = {"client_id", "month", "year"}))
@Getter
@Setter
public class PayrollRun extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @Column(nullable = false)
    private int month;

    @Column(nullable = false)
    private int year;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayrollRunStatus status = PayrollRunStatus.DRAFT;

    @Column(name = "total_gross", precision = 14, scale = 2, nullable = false)
    private BigDecimal totalGross = BigDecimal.ZERO;

    @Column(name = "total_deductions", precision = 14, scale = 2, nullable = false)
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Column(name = "total_net", precision = 14, scale = 2, nullable = false)
    private BigDecimal totalNet = BigDecimal.ZERO;

    @Column(name = "processed_at")
    private Instant processedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by_id")
    private User processedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(length = 1000)
    private String notes;

    @OneToMany(mappedBy = "payrollRun", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PayrollItem> items = new ArrayList<>();
}
