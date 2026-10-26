package com.hrsolution.payroll.service;

import com.hrsolution.audit.entity.AuditAction;
import com.hrsolution.audit.service.AuditService;
import com.hrsolution.attendance.entity.AttendanceEntryStatus;
import com.hrsolution.attendance.entity.AttendanceSheet;
import com.hrsolution.attendance.entity.AttendanceSheetStatus;
import com.hrsolution.attendance.repository.AttendanceSheetRepository;
import com.hrsolution.client.entity.Client;
import com.hrsolution.client.repository.ClientRepository;
import com.hrsolution.client.service.ClientAccessGuard;
import com.hrsolution.common.error.BusinessRuleException;
import com.hrsolution.common.error.ResourceNotFoundException;
import com.hrsolution.common.repository.SpecificationUtils;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.deployment.entity.Deployment;
import com.hrsolution.deployment.repository.DeploymentRepository;
import com.hrsolution.payroll.dto.PayrollDtos;
import com.hrsolution.payroll.entity.PayrollComponentCode;
import com.hrsolution.payroll.entity.PayrollComponentType;
import com.hrsolution.payroll.entity.PayrollItem;
import com.hrsolution.payroll.entity.PayrollItemComponent;
import com.hrsolution.payroll.entity.PayrollRun;
import com.hrsolution.payroll.entity.PayrollRunStatus;
import com.hrsolution.payroll.mapper.PayrollMapper;
import com.hrsolution.payroll.repository.PayrollItemRepository;
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
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollService {

    private final PayrollRunRepository payrollRunRepository;
    private final PayrollItemRepository payrollItemRepository;
    private final AttendanceSheetRepository attendanceSheetRepository;
    private final DeploymentRepository deploymentRepository;
    private final ClientRepository clientRepository;
    private final ClientAccessGuard accessGuard;
    private final PayrollMapper mapper;
    private final AuditService auditService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResponse<PayrollDtos.SummaryResponse> list(Long requestedClientId, Integer month, Integer year,
                                                         PayrollRunStatus status, AuthenticatedUser caller,
                                                         Pageable pageable) {
        Long scopedClientId = accessGuard.resolveAccessibleClientId(requestedClientId, caller);
        Specification<PayrollRun> spec = Specification.<PayrollRun>unrestricted()
                .and(SpecificationUtils.notDeleted())
                .and(SpecificationUtils.equal("client.id", scopedClientId))
                .and(SpecificationUtils.equal("month", month))
                .and(SpecificationUtils.equal("year", year))
                .and(SpecificationUtils.equal("status", status));
        Page<PayrollRun> page = payrollRunRepository.findAll(spec, pageable);
        return PageResponse.from(page, run -> {
            PayrollDtos.SummaryResponse base = mapper.toSummaryResponse(run);
            return new PayrollDtos.SummaryResponse(
                    base.id(), base.clientId(), base.clientName(),
                    base.month(), base.year(), base.status(),
                    base.totalGross(), base.totalNet(),
                    run.getItems().size(),
                    base.processedAt(), base.approvedAt(), base.paidAt(), base.lockedAt());
        });
    }

    @Transactional(readOnly = true)
    public PayrollDtos.Response get(Long runId, AuthenticatedUser caller) {
        PayrollRun run = load(runId);
        if (run.getClient() != null) {
            accessGuard.requireAccessTo(run.getClient().getId(), caller);
        }
        PayrollDtos.Response base = mapper.toResponse(run);
        List<PayrollDtos.ItemResponse> items = run.getItems().stream().map(item -> {
            PayrollDtos.ItemResponse itemBase = mapper.toItemResponse(item);
            List<PayrollDtos.ComponentResponse> comps = item.getComponents().stream()
                    .map(mapper::toComponentResponse)
                    .toList();
            return new PayrollDtos.ItemResponse(
                    itemBase.id(), itemBase.workerId(), itemBase.workerName(),
                    itemBase.employeeCode(), itemBase.deploymentId(), itemBase.attendanceSheetId(),
                    itemBase.paidDays(), itemBase.totalDays(), itemBase.overtimeHours(),
                    itemBase.grossEarnings(), itemBase.totalDeductions(), itemBase.netPay(),
                    itemBase.belowMinimumWage(), comps);
        }).toList();
        return new PayrollDtos.Response(
                base.id(), base.clientId(), base.clientName(),
                base.month(), base.year(), base.status(), base.allowedNextStatuses(),
                base.locked(), base.totalGross(), base.totalDeductions(), base.totalNet(),
                items, base.processedAt(), base.processedBy(), base.approvedAt(), base.approvedBy(),
                base.paidAt(), base.lockedAt(), base.notes(), base.createdAt());
    }

    @Transactional
    public PayrollDtos.Response generate(PayrollDtos.GenerateRequest request,
                                         AuthenticatedUser caller,
                                         RequestContext context) {
        int month = request.month();
        int year = request.year();
        Client client = null;
        if (request.clientId() != null) {
            client = clientRepository.findById(request.clientId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Client", request.clientId()));
            accessGuard.requireAccessTo(client.getId(), caller);
            if (payrollRunRepository.findByClientIdAndMonthAndYear(client.getId(), month, year).isPresent()) {
                throw new BusinessRuleException("A payroll run already exists for this client and period.");
            }
        } else {
            if (payrollRunRepository.findByMonthAndYear(month, year).isPresent()) {
                throw new BusinessRuleException("A payroll run already exists for this period.");
            }
        }

        PayrollRun run = new PayrollRun();
        run.setClient(client);
        run.setMonth(month);
        run.setYear(year);
        run.setStatus(PayrollRunStatus.DRAFT);
        PayrollRun saved = payrollRunRepository.saveAndFlush(run);

        YearMonth ym = YearMonth.of(year, month);
        List<PayrollItem> items = new ArrayList<>();

        List<Deployment> deployments = request.clientId() == null
                ? deploymentRepository.findAll()
                : deploymentRepository.findAll(org.springframework.data.jpa.domain.Specification.<com.hrsolution.deployment.entity.Deployment>unrestricted()
                        .and(com.hrsolution.common.repository.SpecificationUtils.equal("client.id", request.clientId()))
                        .and(com.hrsolution.common.repository.SpecificationUtils.equal("status", com.hrsolution.deployment.entity.DeploymentStatus.ACTIVE)));

        for (Deployment deployment : deployments) {
            if (!deployment.getStatus().name().equals("ACTIVE")) {
                continue;
            }
            AttendanceSheet sheet = attendanceSheetRepository
                    .findBySiteIdAndMonthAndYear(deployment.getSite().getId(), month, year)
                    .filter(s -> s.getStatus() == AttendanceSheetStatus.LOCKED)
                    .orElse(null);
            if (sheet == null) {
                continue;
            }

            int totalDays = ym.lengthOfMonth();
            int paidDays = (int) sheet.getEntries().stream()
                    .filter(e -> e.getWorker().getId().equals(deployment.getWorker().getId())
                            && e.getDeployment().getId().equals(deployment.getId())
                            && e.getStatus() == AttendanceEntryStatus.PRESENT)
                    .count();
            int halfDays = (int) sheet.getEntries().stream()
                    .filter(e -> e.getWorker().getId().equals(deployment.getWorker().getId())
                            && e.getDeployment().getId().equals(deployment.getId())
                            && e.getStatus() == AttendanceEntryStatus.HALF_DAY)
                    .count();
            paidDays += (halfDays / 2);
            if (paidDays > totalDays) {
                paidDays = totalDays;
            }

            BigDecimal monthlyWage = deployment.getMonthlyWage() == null ? BigDecimal.ZERO : deployment.getMonthlyWage();
            BigDecimal dailyRate = monthlyWage.divide(BigDecimal.valueOf(totalDays), 2, RoundingMode.HALF_UP);
            BigDecimal basic = dailyRate.multiply(BigDecimal.valueOf(paidDays));
            BigDecimal gross = basic;
            BigDecimal pfEe = basic.multiply(new BigDecimal("0.12")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal esiEe = basic.compareTo(new BigDecimal("21000")) <= 0
                    ? basic.multiply(new BigDecimal("0.0075")).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal pt = new BigDecimal("200");
            BigDecimal deductions = pfEe.add(esiEe).add(pt);
            BigDecimal net = gross.subtract(deductions);

            PayrollItem item = new PayrollItem();
            item.setPayrollRun(saved);
            item.setWorker(deployment.getWorker());
            item.setDeployment(deployment);
            item.setAttendanceSheet(sheet);
            item.setPaidDays(paidDays);
            item.setTotalDays(totalDays);
            item.setOvertimeHours(BigDecimal.ZERO);
            item.setGrossEarnings(gross);
            item.setTotalDeductions(deductions);
            item.setNetPay(net);
            item.setBelowMinimumWage(false);

            addComponent(item, PayrollComponentType.EARNING, PayrollComponentCode.BASIC, basic, basic, new BigDecimal("100.000"));
            if (pfEe.compareTo(BigDecimal.ZERO) > 0) {
                addComponent(item, PayrollComponentType.DEDUCTION, PayrollComponentCode.PF_EE, pfEe, basic, new BigDecimal("12.000"));
            }
            if (esiEe.compareTo(BigDecimal.ZERO) > 0) {
                addComponent(item, PayrollComponentType.DEDUCTION, PayrollComponentCode.ESI_EE, esiEe, basic, new BigDecimal("0.750"));
            }
            addComponent(item, PayrollComponentType.DEDUCTION, PayrollComponentCode.PT, pt, null, null);
            addComponent(item, PayrollComponentType.EMPLOYER_CONTRIBUTION, PayrollComponentCode.PF_ER, pfEe, basic, new BigDecimal("12.000"));
            if (esiEe.compareTo(BigDecimal.ZERO) > 0) {
                addComponent(item, PayrollComponentType.EMPLOYER_CONTRIBUTION, PayrollComponentCode.ESI_ER, basic.multiply(new BigDecimal("0.0325")).setScale(2, RoundingMode.HALF_UP), basic, new BigDecimal("3.250"));
            }

            items.add(item);
        }

        payrollItemRepository.saveAll(items);

        saved.setTotalGross(items.stream().map(PayrollItem::getGrossEarnings).reduce(BigDecimal.ZERO, BigDecimal::add));
        saved.setTotalDeductions(items.stream().map(PayrollItem::getTotalDeductions).reduce(BigDecimal.ZERO, BigDecimal::add));
        saved.setTotalNet(items.stream().map(PayrollItem::getNetPay).reduce(BigDecimal.ZERO, BigDecimal::add));

        auditService.recordEntityChange(AuditAction.CREATE, "PayrollRun", saved.getId(),
                caller.id(), caller.email(), "Generated payroll run %d-%02d".formatted(year, month),
                null, null, context);

        return get(saved.getId(), caller);
    }

    private void addComponent(PayrollItem item, PayrollComponentType type, PayrollComponentCode code,
                              BigDecimal amount, BigDecimal computedOn, BigDecimal ratePercent) {
        PayrollItemComponent comp = new PayrollItemComponent();
        comp.setPayrollItem(item);
        comp.setComponentType(type);
        comp.setCode(code);
        comp.setAmount(amount);
        comp.setComputedOn(computedOn);
        comp.setRatePercent(ratePercent);
        item.getComponents().add(comp);
    }

    @Transactional
    public PayrollDtos.Response process(Long runId, AuthenticatedUser caller, RequestContext context) {
        PayrollRun run = load(runId);
        if (!run.getStatus().canProcess()) {
            throw new BusinessRuleException("Only DRAFT payroll runs can be processed.");
        }
        run.setStatus(PayrollRunStatus.PROCESSED);
        run.setProcessedAt(Instant.now());
        run.setProcessedBy(userRepository.findById(caller.id()).orElse(null));
        auditService.recordEntityChange(AuditAction.UPDATE, "PayrollRun", runId,
                caller.id(), caller.email(), "Processed payroll run", null, null, context);
        return get(runId, caller);
    }

    @Transactional
    public PayrollDtos.Response approve(Long runId, AuthenticatedUser caller, RequestContext context) {
        PayrollRun run = load(runId);
        if (!run.getStatus().canApprove()) {
            throw new BusinessRuleException("Only PROCESSED payroll runs can be approved.");
        }
        run.setStatus(PayrollRunStatus.APPROVED);
        run.setApprovedAt(Instant.now());
        run.setApprovedBy(userRepository.findById(caller.id()).orElse(null));
        auditService.recordEntityChange(AuditAction.UPDATE, "PayrollRun", runId,
                caller.id(), caller.email(), "Approved payroll run", null, null, context);
        return get(runId, caller);
    }

    @Transactional
    public PayrollDtos.Response markPaid(Long runId, AuthenticatedUser caller, RequestContext context) {
        PayrollRun run = load(runId);
        if (!run.getStatus().canMarkPaid()) {
            throw new BusinessRuleException("Only APPROVED payroll runs can be marked paid.");
        }
        run.setStatus(PayrollRunStatus.PAID);
        run.setPaidAt(Instant.now());
        auditService.recordEntityChange(AuditAction.UPDATE, "PayrollRun", runId,
                caller.id(), caller.email(), "Marked payroll run as paid", null, null, context);
        return get(runId, caller);
    }

    @Transactional
    public PayrollDtos.Response lock(Long runId, AuthenticatedUser caller, RequestContext context) {
        PayrollRun run = load(runId);
        if (!run.getStatus().canLock()) {
            throw new BusinessRuleException("Only PAID payroll runs can be locked.");
        }
        run.setStatus(PayrollRunStatus.LOCKED);
        run.setLockedAt(Instant.now());
        auditService.recordEntityChange(AuditAction.UPDATE, "PayrollRun", runId,
                caller.id(), caller.email(), "Locked payroll run", null, null, context);
        return get(runId, caller);
    }

    private PayrollRun load(Long runId) {
        return payrollRunRepository.findById(runId)
                .filter(r -> !r.isDeleted())
                .orElseThrow(() -> ResourceNotFoundException.of("PayrollRun", runId));
    }
}
