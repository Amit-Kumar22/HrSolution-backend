package com.hrsolution.attendance.entity;

import com.hrsolution.client.entity.Client;
import com.hrsolution.client.entity.ClientSite;
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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "attendance_sheets",
        uniqueConstraints = @UniqueConstraint(name = "uk_as_site_month_year",
                columnNames = {"site_id", "month", "year"}))
@Getter
@Setter
public class AttendanceSheet extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private ClientSite site;

    @Column(nullable = false)
    private int month;

    @Column(nullable = false)
    private int year;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceSheetStatus status = AttendanceSheetStatus.DRAFT;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by_id")
    private User submittedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private User approvedBy;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "locked_by_id")
    private User lockedBy;

    @Column(name = "unlock_reason", length = 1000)
    private String unlockReason;

    @Column(name = "unlocked_at")
    private Instant unlockedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unlocked_by_id")
    private User unlockedBy;

    @Column(length = 1000)
    private String remarks;

    @OneToMany(mappedBy = "sheet", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("attendanceDate ASC, id ASC")
    private List<AttendanceEntry> entries = new ArrayList<>();
}
