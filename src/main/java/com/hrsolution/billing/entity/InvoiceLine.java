package com.hrsolution.billing.entity;

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
@Table(name = "invoice_lines")
@Getter
@Setter
public class InvoiceLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "sac_code", length = 10)
    private String sacCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false, length = 20)
    private InvoiceLineType lineType;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal rate = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "gst_rate", precision = 5, scale = 2, nullable = false)
    private BigDecimal gstRate = new BigDecimal("18.00");
}
