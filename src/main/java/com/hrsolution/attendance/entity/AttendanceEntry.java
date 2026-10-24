package com.hrsolution.attendance.entity;

import com.hrsolution.common.domain.BaseEntity;
import com.hrsolution.deployment.entity.Deployment;
import com.hrsolution.worker.entity.Worker;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "attendance_entries",
        uniqueConstraints = @UniqueConstraint(name = "uk_ae_sheet_worker_date",
                columnNames = {"sheet_id", "worker_id", "attendance_date"}))
@Getter
@Setter
public class AttendanceEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sheet_id", nullable = false)
    private AttendanceSheet sheet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deployment_id", nullable = false)
    private Deployment deployment;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceEntryStatus status = AttendanceEntryStatus.PRESENT;

    @Column(name = "overtime_hours", precision = 4, scale = 2, nullable = false)
    private BigDecimal overtimeHours = BigDecimal.ZERO;

    @Column(length = 500)
    private String remarks;
}
