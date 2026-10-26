package com.hrsolution.payroll.entity;

import com.hrsolution.attendance.entity.AttendanceSheet;
import com.hrsolution.common.domain.BaseEntity;
import com.hrsolution.deployment.entity.Deployment;
import com.hrsolution.worker.entity.Worker;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "payroll_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_pi_run_worker_deployment",
                columnNames = {"payroll_run_id", "worker_id", "deployment_id"}))
@Getter
@Setter
public class PayrollItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_run_id", nullable = false)
    private PayrollRun payrollRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deployment_id", nullable = false)
    private Deployment deployment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_sheet_id")
    private AttendanceSheet attendanceSheet;

    @Column(name = "paid_days", nullable = false)
    private int paidDays;

    @Column(name = "total_days", nullable = false)
    private int totalDays;

    @Column(name = "overtime_hours", precision = 6, scale = 2, nullable = false)
    private BigDecimal overtimeHours = BigDecimal.ZERO;

    @Column(name = "gross_earnings", precision = 14, scale = 2, nullable = false)
    private BigDecimal grossEarnings = BigDecimal.ZERO;

    @Column(name = "total_deductions", precision = 14, scale = 2, nullable = false)
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Column(name = "net_pay", precision = 14, scale = 2, nullable = false)
    private BigDecimal netPay = BigDecimal.ZERO;

    @Column(name = "below_minimum_wage", nullable = false)
    private boolean belowMinimumWage;

    @OneToMany(mappedBy = "payrollItem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PayrollItemComponent> components = new ArrayList<>();
}
