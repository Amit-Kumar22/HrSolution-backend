package com.hrsolution.compliance.entity;

import com.hrsolution.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "statutory_challans")
@Getter
@Setter
public class StatutoryChallan extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "challan_type", nullable = false, length = 10)
    private ChallanType challanType;

    @Column(nullable = false)
    private int month;

    @Column(nullable = false)
    private int year;

    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "paid_on")
    private LocalDate paidOn;

    @Column(name = "challan_number", length = 100)
    private String challanNumber;

    @Column(name = "document_path", length = 500)
    private String documentPath;

    @Column(name = "return_filed", nullable = false)
    private boolean returnFiled;

    @Column(length = 1000)
    private String notes;
}
