package com.hrsolution.billing.dto;

import com.hrsolution.billing.entity.InvoiceLineType;
import com.hrsolution.billing.entity.InvoiceStatus;
import com.hrsolution.billing.entity.PaymentMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class BillingDtos {
    private BillingDtos() {
    }

    public record GenerateFromPayrollRequest(
            @NotNull Long payrollRunId,
            @NotNull LocalDate invoiceDate,
            @NotNull LocalDate dueDate) {
    }

    public record LineRequest(
            @NotBlank String description,
            String sacCode,
            @NotNull InvoiceLineType lineType,
            BigDecimal quantity,
            @NotNull BigDecimal rate,
            BigDecimal gstRate) {
    }

    public record LineResponse(
            Long id,
            String description,
            String sacCode,
            InvoiceLineType lineType,
            BigDecimal quantity,
            BigDecimal rate,
            BigDecimal amount,
            BigDecimal gstRate) {
    }

    public record PaymentRequest(
            @NotNull LocalDate paymentDate,
            @NotNull BigDecimal amount,
            @NotNull PaymentMode mode,
            String referenceNumber,
            BigDecimal tdsDeducted,
            String remarks) {
    }

    public record PaymentResponse(
            Long id,
            LocalDate paymentDate,
            BigDecimal amount,
            PaymentMode mode,
            String referenceNumber,
            BigDecimal tdsDeducted,
            String remarks) {
    }

    public record CreditNoteRequest(
            @NotNull LocalDate issueDate,
            @NotNull BigDecimal amount,
            @NotBlank String reason) {
    }

    public record CreditNoteResponse(
            Long id,
            String creditNoteNumber,
            LocalDate issueDate,
            BigDecimal amount,
            String reason) {
    }

    public record Response(
            Long id,
            String invoiceNumber,
            Long clientId,
            String clientName,
            Long payrollRunId,
            LocalDate invoiceDate,
            LocalDate dueDate,
            String placeOfSupply,
            BigDecimal taxableAmount,
            BigDecimal cgstAmount,
            BigDecimal sgstAmount,
            BigDecimal igstAmount,
            BigDecimal totalAmount,
            BigDecimal amountPaid,
            BigDecimal balanceDue,
            InvoiceStatus status,
            List<InvoiceStatus> allowedNextStatuses,
            boolean editable,
            String notes,
            List<LineResponse> lines,
            List<PaymentResponse> payments,
            List<CreditNoteResponse> creditNotes,
            Instant issuedAt,
            String issuedBy,
            Instant cancelledAt,
            String cancelledBy,
            String cancelReason,
            Instant createdAt) {
    }

    public record SummaryResponse(
            Long id,
            String invoiceNumber,
            String clientName,
            LocalDate invoiceDate,
            LocalDate dueDate,
            BigDecimal totalAmount,
            BigDecimal amountPaid,
            BigDecimal balanceDue,
            InvoiceStatus status) {
    }
}
