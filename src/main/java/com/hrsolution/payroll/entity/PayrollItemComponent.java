package com.hrsolution.payroll.entity;

import com.hrsolution.common.domain.BaseEntity;
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

import java.math.BigDecimal;

@Entity
@Table(name = "payroll_item_components")
@Getter
@Setter
public class PayrollItemComponent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_item_id", nullable = false)
    private PayrollItem payrollItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "component_type", nullable = false, length = 20)
    private PayrollComponentType componentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayrollComponentCode code;

    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "computed_on", precision = 14, scale = 2)
    private BigDecimal computedOn;

    @Column(name = "rate_percent", precision = 6, scale = 3)
    private BigDecimal ratePercent;
}
