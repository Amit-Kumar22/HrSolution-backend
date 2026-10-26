package com.hrsolution.billing.mapper;

import com.hrsolution.billing.dto.BillingDtos;
import com.hrsolution.billing.entity.CreditNote;
import com.hrsolution.billing.entity.Invoice;
import com.hrsolution.billing.entity.InvoiceLine;
import com.hrsolution.billing.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface BillingMapper {

    @Mapping(target = "clientId", expression = "java(invoice.getClient().getId())")
    @Mapping(target = "clientName", expression = "java(invoice.getClient().getTradeName() != null ? invoice.getClient().getTradeName() : invoice.getClient().getLegalName())")
    @Mapping(target = "payrollRunId", expression = "java(invoice.getPayrollRun() == null ? null : invoice.getPayrollRun().getId())")
    @Mapping(target = "balanceDue", expression = "java(invoice.balanceDue())")
    @Mapping(target = "allowedNextStatuses", expression = "java(invoice.getStatus().allowedNext())")
    @Mapping(target = "editable", expression = "java(invoice.getStatus().canEdit())")
    @Mapping(target = "issuedBy", expression = "java(invoice.getIssuedBy() == null ? null : invoice.getIssuedBy().fullName())")
    @Mapping(target = "cancelledBy", expression = "java(invoice.getCancelledBy() == null ? null : invoice.getCancelledBy().fullName())")
    @Mapping(target = "lines", ignore = true)
    @Mapping(target = "payments", ignore = true)
    @Mapping(target = "creditNotes", ignore = true)
    BillingDtos.Response toResponse(Invoice invoice);

    @Mapping(target = "clientName", expression = "java(invoice.getClient().getTradeName() != null ? invoice.getClient().getTradeName() : invoice.getClient().getLegalName())")
    @Mapping(target = "balanceDue", expression = "java(invoice.balanceDue())")
    BillingDtos.SummaryResponse toSummaryResponse(Invoice invoice);

    BillingDtos.LineResponse toLineResponse(InvoiceLine line);

    BillingDtos.PaymentResponse toPaymentResponse(Payment payment);

    BillingDtos.CreditNoteResponse toCreditNoteResponse(CreditNote creditNote);
}
