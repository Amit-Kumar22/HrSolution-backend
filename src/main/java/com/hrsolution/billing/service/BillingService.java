package com.hrsolution.billing.service;

import com.hrsolution.audit.entity.AuditAction;
import com.hrsolution.audit.service.AuditService;
import com.hrsolution.billing.dto.BillingDtos;
import com.hrsolution.billing.entity.CreditNote;
import com.hrsolution.billing.entity.Invoice;
import com.hrsolution.billing.entity.InvoiceLine;
import com.hrsolution.billing.entity.InvoiceLineType;
import com.hrsolution.billing.entity.InvoiceStatus;
import com.hrsolution.billing.entity.Payment;
import com.hrsolution.billing.mapper.BillingMapper;
import com.hrsolution.billing.repository.CreditNoteRepository;
import com.hrsolution.billing.repository.InvoiceRepository;
import com.hrsolution.billing.repository.PaymentRepository;
import com.hrsolution.client.entity.Client;
import com.hrsolution.client.service.ClientAccessGuard;
import com.hrsolution.common.error.BusinessRuleException;
import com.hrsolution.common.error.ResourceNotFoundException;
import com.hrsolution.common.repository.SpecificationUtils;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.payroll.entity.PayrollItem;
import com.hrsolution.payroll.entity.PayrollRun;
import com.hrsolution.payroll.repository.PayrollRunRepository;
import com.hrsolution.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final CreditNoteRepository creditNoteRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final ClientAccessGuard accessGuard;
    private final BillingMapper mapper;
    private final AuditService auditService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResponse<BillingDtos.SummaryResponse> list(Long requestedClientId, InvoiceStatus status,
                                                          AuthenticatedUser caller, Pageable pageable) {
        Long scopedClientId = accessGuard.resolveAccessibleClientId(requestedClientId, caller);
        Specification<Invoice> spec = Specification.<Invoice>unrestricted()
                .and(SpecificationUtils.notDeleted())
                .and(SpecificationUtils.equal("client.id", scopedClientId))
                .and(SpecificationUtils.equal("status", status));
        Page<Invoice> page = invoiceRepository.findAll(spec, pageable);
        return PageResponse.from(page, invoice -> {
            BillingDtos.SummaryResponse base = mapper.toSummaryResponse(invoice);
            return new BillingDtos.SummaryResponse(
                    base.id(), base.invoiceNumber(), base.clientName(),
                    base.invoiceDate(), base.dueDate(),
                    base.totalAmount(), base.amountPaid(), invoice.balanceDue(), base.status());
        });
    }

    @Transactional(readOnly = true)
    public BillingDtos.Response get(Long invoiceId, AuthenticatedUser caller) {
        Invoice invoice = load(invoiceId);
        accessGuard.requireAccessTo(invoice.getClient().getId(), caller);
        BillingDtos.Response base = mapper.toResponse(invoice);
        return new BillingDtos.Response(
                base.id(), base.invoiceNumber(), base.clientId(), base.clientName(),
                base.payrollRunId(), base.invoiceDate(), base.dueDate(), base.placeOfSupply(),
                base.taxableAmount(), base.cgstAmount(), base.sgstAmount(), base.igstAmount(),
                base.totalAmount(), base.amountPaid(), invoice.balanceDue(), base.status(),
                base.allowedNextStatuses(), base.editable(), base.notes(),
                invoice.getLines().stream().map(mapper::toLineResponse).toList(),
                invoice.getPayments().stream().map(mapper::toPaymentResponse).toList(),
                invoice.getCreditNotes().stream().map(mapper::toCreditNoteResponse).toList(),
                base.issuedAt(), base.issuedBy(), base.cancelledAt(), base.cancelledBy(),
                base.cancelReason(), base.createdAt());
    }

    @Transactional
    public BillingDtos.Response generateFromPayroll(BillingDtos.GenerateFromPayrollRequest request,
                                                    AuthenticatedUser caller,
                                                    RequestContext context) {
        PayrollRun payrollRun = payrollRunRepository.findById(request.payrollRunId())
                .filter(r -> !r.isDeleted())
                .orElseThrow(() -> ResourceNotFoundException.of("PayrollRun", request.payrollRunId()));
        if (payrollRun.getClient() == null) {
            throw new BusinessRuleException("Payroll run must be for a specific client to generate invoice.");
        }
        accessGuard.requireAccessTo(payrollRun.getClient().getId(), caller);

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(generateInvoiceNumber());
        invoice.setClient(payrollRun.getClient());
        invoice.setPayrollRun(payrollRun);
        invoice.setInvoiceDate(request.invoiceDate());
        invoice.setDueDate(request.dueDate());
        invoice.setPlaceOfSupply(payrollRun.getClient().getBillingStateCode());
        invoice.setStatus(InvoiceStatus.DRAFT);

        BigDecimal taxable = BigDecimal.ZERO;
        for (PayrollItem item : payrollRun.getItems()) {
            InvoiceLine line = new InvoiceLine();
            line.setInvoice(invoice);
            line.setDescription("Contract labour charges - " + item.getWorker().fullName() + " (" + item.getWorker().employeeCode() + ")");
            line.setSacCode("998519");
            line.setLineType(InvoiceLineType.WAGES);
            line.setQuantity(BigDecimal.valueOf(item.getPaidDays()));
            line.setRate(item.getDeployment().getBillingRate());
            line.setAmount(line.getRate().multiply(line.getQuantity()));
            line.setGstRate(new BigDecimal("18.00"));
            invoice.getLines().add(line);
            taxable = taxable.add(line.getAmount());
        }

        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;
        BigDecimal igst = BigDecimal.ZERO;
        String companyState = "MH"; // default
        if (payrollRun.getClient().getBillingStateCode() != null
                && payrollRun.getClient().getBillingStateCode().equals(companyState)) {
            cgst = taxable.multiply(new BigDecimal("0.09"));
            sgst = taxable.multiply(new BigDecimal("0.09"));
        } else if (payrollRun.getClient().getBillingStateCode() != null) {
            igst = taxable.multiply(new BigDecimal("0.18"));
        } else {
            // missing means UNKNOWN - Phase 9 note says refuse to invoice rather than guess
            throw new BusinessRuleException("Client billing state code is missing; cannot determine GST treatment.");
        }

        invoice.setTaxableAmount(taxable);
        invoice.setCgstAmount(cgst);
        invoice.setSgstAmount(sgst);
        invoice.setIgstAmount(igst);
        invoice.setTotalAmount(taxable.add(cgst).add(sgst).add(igst));
        invoice.setAmountPaid(BigDecimal.ZERO);

        Invoice saved = invoiceRepository.saveAndFlush(invoice);

        auditService.recordEntityChange(AuditAction.CREATE, "Invoice", saved.getId(),
                caller.id(), caller.email(), "Generated invoice " + saved.getInvoiceNumber(),
                null, saved.getInvoiceNumber(), context);

        return get(saved.getId(), caller);
    }

    @Transactional
    public BillingDtos.Response issue(Long invoiceId, AuthenticatedUser caller, RequestContext context) {
        Invoice invoice = load(invoiceId);
        if (!invoice.getStatus().canIssue()) {
            throw new BusinessRuleException("Only DRAFT invoices can be issued.");
        }
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setIssuedAt(Instant.now());
        invoice.setIssuedBy(userRepository.findById(caller.id()).orElse(null));
        auditService.recordEntityChange(AuditAction.UPDATE, "Invoice", invoiceId,
                caller.id(), caller.email(), "Issued invoice " + invoice.getInvoiceNumber(),
                null, null, context);
        return get(invoiceId, caller);
    }

    @Transactional
    public BillingDtos.Response recordPayment(Long invoiceId, BillingDtos.PaymentRequest request,
                                             AuthenticatedUser caller, RequestContext context) {
        Invoice invoice = load(invoiceId);
        if (!invoice.getStatus().isIssued()) {
            throw new BusinessRuleException("Payments can only be recorded against issued invoices.");
        }
        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setPaymentDate(request.paymentDate());
        payment.setAmount(request.amount());
        payment.setMode(request.mode());
        payment.setReferenceNumber(request.referenceNumber());
        payment.setTdsDeducted(request.tdsDeducted() == null ? BigDecimal.ZERO : request.tdsDeducted());
        payment.setRemarks(request.remarks());
        paymentRepository.save(payment);

        BigDecimal totalPaid = invoice.getPayments().stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        totalPaid = totalPaid.add(request.amount());
        invoice.setAmountPaid(totalPaid);

        if (totalPaid.compareTo(invoice.getTotalAmount()) >= 0) {
            invoice.setStatus(InvoiceStatus.PAID);
        } else if (totalPaid.compareTo(BigDecimal.ZERO) > 0) {
            invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
        }

        auditService.recordEntityChange(AuditAction.UPDATE, "Invoice", invoiceId,
                caller.id(), caller.email(), "Recorded payment of " + request.amount(),
                null, null, context);
        return get(invoiceId, caller);
    }

    @Transactional
    public BillingDtos.CreditNoteResponse createCreditNote(Long invoiceId, BillingDtos.CreditNoteRequest request,
                                                           AuthenticatedUser caller, RequestContext context) {
        Invoice invoice = load(invoiceId);
        if (!invoice.getStatus().isIssued()) {
            throw new BusinessRuleException("Credit notes can only be issued against issued invoices.");
        }
        CreditNote cn = new CreditNote();
        cn.setCreditNoteNumber(generateCreditNoteNumber());
        cn.setInvoice(invoice);
        cn.setIssueDate(request.issueDate());
        cn.setAmount(request.amount());
        cn.setReason(request.reason());
        CreditNote saved = creditNoteRepository.save(cn);

        auditService.recordEntityChange(AuditAction.CREATE, "CreditNote", saved.getId(),
                caller.id(), caller.email(), "Created credit note " + saved.getCreditNoteNumber(),
                null, saved.getCreditNoteNumber(), context);

        return mapper.toCreditNoteResponse(saved);
    }

    @Transactional
    public void cancel(Long invoiceId, String reason, AuthenticatedUser caller, RequestContext context) {
        Invoice invoice = load(invoiceId);
        if (!invoice.getStatus().canIssue()) {
            throw new BusinessRuleException("Only DRAFT invoices can be cancelled.");
        }
        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoice.setCancelledAt(Instant.now());
        invoice.setCancelledBy(userRepository.findById(caller.id()).orElse(null));
        invoice.setCancelReason(reason);
        auditService.recordEntityChange(AuditAction.UPDATE, "Invoice", invoiceId,
                caller.id(), caller.email(), "Cancelled invoice: " + reason,
                null, null, context);
    }

    private String generateInvoiceNumber() {
        String prefix = "INV/" + java.time.Year.now().getValue() + "-" + ((java.time.Year.now().getValue() % 100) + 1) + "/";
        long count = invoiceRepository.countByInvoiceNumberStartingWith(prefix);
        return prefix + String.format("%04d", count + 1);
    }

    private String generateCreditNoteNumber() {
        String prefix = "CN/" + java.time.Year.now().getValue() + "/";
        long count = creditNoteRepository.count();
        return prefix + String.format("%04d", count + 1);
    }

    private Invoice load(Long invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .filter(i -> !i.isDeleted())
                .orElseThrow(() -> ResourceNotFoundException.of("Invoice", invoiceId));
    }
}
