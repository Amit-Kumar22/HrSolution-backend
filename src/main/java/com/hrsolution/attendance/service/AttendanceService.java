package com.hrsolution.attendance.service;

import com.hrsolution.audit.entity.AuditAction;
import com.hrsolution.audit.service.AuditService;
import com.hrsolution.attendance.dto.AttendanceDtos;
import com.hrsolution.attendance.entity.AttendanceEntry;
import com.hrsolution.attendance.entity.AttendanceEntryStatus;
import com.hrsolution.attendance.entity.AttendanceSheet;
import com.hrsolution.attendance.entity.AttendanceSheetStatus;
import com.hrsolution.attendance.mapper.AttendanceMapper;
import com.hrsolution.attendance.repository.AttendanceEntryRepository;
import com.hrsolution.attendance.repository.AttendanceSheetRepository;
import com.hrsolution.client.entity.ClientSite;
import com.hrsolution.client.repository.ClientSiteRepository;
import com.hrsolution.client.service.ClientAccessGuard;
import com.hrsolution.common.error.BusinessRuleException;
import com.hrsolution.common.error.ResourceNotFoundException;
import com.hrsolution.common.repository.SpecificationUtils;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.deployment.entity.Deployment;
import com.hrsolution.deployment.repository.DeploymentRepository;
import com.hrsolution.user.repository.UserRepository;
import com.hrsolution.worker.service.WorkerService;
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
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceSheetRepository sheetRepository;
    private final AttendanceEntryRepository entryRepository;
    private final ClientSiteRepository siteRepository;
    private final DeploymentRepository deploymentRepository;
    private final ClientAccessGuard accessGuard;
    private final AttendanceMapper mapper;
    private final AuditService auditService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResponse<AttendanceDtos.SummaryResponse> list(Long requestedClientId,
                                                             Long siteId,
                                                             int month,
                                                             int year,
                                                             AttendanceSheetStatus status,
                                                             AuthenticatedUser caller,
                                                             Pageable pageable) {
        Long scopedClientId = accessGuard.resolveAccessibleClientId(requestedClientId, caller);

        Specification<AttendanceSheet> spec = Specification.<AttendanceSheet>unrestricted()
                .and(SpecificationUtils.notDeleted())
                .and(SpecificationUtils.equal("client.id", scopedClientId))
                .and(SpecificationUtils.equal("site.id", siteId))
                .and(SpecificationUtils.equal("month", month == 0 ? null : month))
                .and(SpecificationUtils.equal("year", year == 0 ? null : year))
                .and(SpecificationUtils.equal("status", status));

        Page<AttendanceSheet> page = sheetRepository.findAll(spec, pageable);
        return PageResponse.from(page, sheet -> {
            AttendanceDtos.SummaryResponse base = mapper.toSummaryResponse(sheet);
            return new AttendanceDtos.SummaryResponse(
                    base.id(), base.clientId(), base.clientName(),
                    base.siteId(), base.siteCode(), base.siteName(),
                    base.month(), base.year(), base.status(),
                    sheet.getEntries().size(),
                    base.submittedAt(), base.approvedAt(), base.lockedAt());
        });
    }

    @Transactional(readOnly = true)
    public AttendanceDtos.Response get(Long sheetId, AuthenticatedUser caller) {
        AttendanceSheet sheet = load(sheetId);
        accessGuard.requireAccessTo(sheet.getClient().getId(), caller);
        AttendanceDtos.Response base = mapper.toResponse(sheet);
        List<AttendanceDtos.EntryResponse> entries = sheet.getEntries().stream()
                .map(mapper::toEntryResponse)
                .toList();
        return new AttendanceDtos.Response(
                base.id(), base.clientId(), base.clientName(),
                base.siteId(), base.siteCode(), base.siteName(),
                base.month(), base.year(), base.status(), base.allowedNextStatuses(),
                base.locked(), base.submittedAt(), base.submittedBy(),
                base.approvedAt(), base.approvedBy(), base.lockedAt(), base.lockedBy(),
                base.unlockReason(), base.unlockedAt(), base.unlockedBy(),
                entries, base.createdAt());
    }

    @Transactional
    public AttendanceDtos.Response generate(Long siteId, int month, int year,
                                            AuthenticatedUser caller,
                                            RequestContext context) {
        ClientSite site = siteRepository.findActiveById(siteId)
                .orElseThrow(() -> ResourceNotFoundException.of("Site", siteId));
        accessGuard.requireAccessTo(site.getClient().getId(), caller);

        if (sheetRepository.findBySiteIdAndMonthAndYear(siteId, month, year).isPresent()) {
            throw new BusinessRuleException("An attendance sheet already exists for this site and period.");
        }

        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        List<Deployment> deployments = deploymentRepository.findActiveAtSiteForPeriod(siteId, start, end);
        if (deployments.isEmpty()) {
            throw new BusinessRuleException("No active deployments found for this site in the period.");
        }

        AttendanceSheet sheet = new AttendanceSheet();
        sheet.setClient(site.getClient());
        sheet.setSite(site);
        sheet.setMonth(month);
        sheet.setYear(year);
        sheet.setStatus(AttendanceSheetStatus.DRAFT);
        AttendanceSheet saved = sheetRepository.saveAndFlush(sheet);

        List<AttendanceEntry> entries = new ArrayList<>();
        for (Deployment deployment : deployments) {
            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                if (!deployment.isInForceOn(date)) {
                    continue;
                }
                AttendanceEntry entry = new AttendanceEntry();
                entry.setSheet(saved);
                entry.setWorker(deployment.getWorker());
                entry.setDeployment(deployment);
                entry.setAttendanceDate(date);
                entry.setStatus(AttendanceEntryStatus.PRESENT);
                entry.setOvertimeHours(BigDecimal.ZERO);
                entries.add(entry);
            }
        }
        entryRepository.saveAll(entries);

        auditService.recordEntityChange(AuditAction.CREATE, "AttendanceSheet", saved.getId(),
                caller.id(), caller.email(), "Generated attendance sheet for %s %d/%d"
                        .formatted(site.getSiteCode(), month, year), null, null, context);

        return get(saved.getId(), caller);
    }

    @Transactional
    public AttendanceDtos.Response saveEntries(Long sheetId,
                                               AttendanceDtos.BulkEntryRequest request,
                                               AuthenticatedUser caller,
                                               RequestContext context) {
        AttendanceSheet sheet = load(sheetId);
        accessGuard.requireAccessTo(sheet.getClient().getId(), caller);
        if (!sheet.getStatus().canEdit()) {
            throw new BusinessRuleException("This attendance sheet cannot be edited in its current status.");
        }

        for (AttendanceDtos.EntryRequest er : request.entries()) {
            AttendanceEntry entry = entryRepository.findById(er.workerId() == null ? -1 : er.workerId())
                    .orElse(null);
            // fallback: find by sheet+worker+date if id not passed differently; keep simple by updating via mapping
        }

        // For simplicity in this implementation: replace entries for provided dates/workers
        if (request.entries() != null) {
            for (AttendanceDtos.EntryRequest er : request.entries()) {
                sheet.getEntries().stream()
                        .filter(e -> e.getWorker().getId().equals(er.workerId())
                                && e.getAttendanceDate().equals(er.attendanceDate()))
                        .findFirst()
                        .ifPresent(e -> {
                            e.setStatus(er.status());
                            e.setOvertimeHours(er.overtimeHours() == null ? BigDecimal.ZERO : er.overtimeHours());
                            e.setRemarks(er.remarks());
                        });
            }
        }

        auditService.recordEntityChange(AuditAction.UPDATE, "AttendanceSheet", sheetId,
                caller.id(), caller.email(), "Updated attendance entries", null, null, context);
        return get(sheetId, caller);
    }

    @Transactional
    public AttendanceDtos.Response submit(Long sheetId, AuthenticatedUser caller, RequestContext context) {
        AttendanceSheet sheet = load(sheetId);
        accessGuard.requireAccessTo(sheet.getClient().getId(), caller);
        if (!sheet.getStatus().canSubmit()) {
            throw new BusinessRuleException("Only DRAFT sheets can be submitted.");
        }
        sheet.setStatus(AttendanceSheetStatus.SUBMITTED);
        sheet.setSubmittedAt(Instant.now());
        sheet.setSubmittedBy(userRepository.findById(caller.id()).orElse(null));
        auditService.recordEntityChange(AuditAction.UPDATE, "AttendanceSheet", sheetId,
                caller.id(), caller.email(), "Submitted attendance sheet", null, null, context);
        return get(sheetId, caller);
    }

    @Transactional
    public AttendanceDtos.Response approve(Long sheetId, AuthenticatedUser caller, RequestContext context) {
        AttendanceSheet sheet = load(sheetId);
        accessGuard.requireAccessTo(sheet.getClient().getId(), caller);
        if (!sheet.getStatus().canClientApprove()) {
            throw new BusinessRuleException("Only SUBMITTED sheets can be approved.");
        }
        sheet.setStatus(AttendanceSheetStatus.CLIENT_APPROVED);
        sheet.setApprovedAt(Instant.now());
        sheet.setApprovedBy(userRepository.findById(caller.id()).orElse(null));
        auditService.recordEntityChange(AuditAction.UPDATE, "AttendanceSheet", sheetId,
                caller.id(), caller.email(), "Approved attendance sheet", null, null, context);
        return get(sheetId, caller);
    }

    @Transactional
    public AttendanceDtos.Response lock(Long sheetId, AuthenticatedUser caller, RequestContext context) {
        AttendanceSheet sheet = load(sheetId);
        accessGuard.requireAccessTo(sheet.getClient().getId(), caller);
        if (!sheet.getStatus().canLock()) {
            throw new BusinessRuleException("Only CLIENT_APPROVED sheets can be locked.");
        }
        sheet.setStatus(AttendanceSheetStatus.LOCKED);
        sheet.setLockedAt(Instant.now());
        sheet.setLockedBy(userRepository.findById(caller.id()).orElse(null));
        auditService.recordEntityChange(AuditAction.UPDATE, "AttendanceSheet", sheetId,
                caller.id(), caller.email(), "Locked attendance sheet", null, null, context);
        return get(sheetId, caller);
    }

    @Transactional
    public AttendanceDtos.Response unlock(Long sheetId, AttendanceDtos.UnlockRequest request,
                                         AuthenticatedUser caller, RequestContext context) {
        AttendanceSheet sheet = load(sheetId);
        accessGuard.requireAccessTo(sheet.getClient().getId(), caller);
        if (!sheet.getStatus().isLocked()) {
            throw new BusinessRuleException("Only LOCKED sheets can be unlocked.");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw new BusinessRuleException("An unlock reason is required.");
        }
        sheet.setStatus(AttendanceSheetStatus.DRAFT);
        sheet.setUnlockReason(request.reason());
        sheet.setUnlockedAt(Instant.now());
        sheet.setUnlockedBy(userRepository.findById(caller.id()).orElse(null));
        sheet.setLockedAt(null);
        sheet.setLockedBy(null);
        auditService.recordEntityChange(AuditAction.UPDATE, "AttendanceSheet", sheetId,
                caller.id(), caller.email(), "Unlocked attendance sheet: " + request.reason(),
                null, null, context);
        return get(sheetId, caller);
    }

    private AttendanceSheet load(Long sheetId) {
        return sheetRepository.findById(sheetId)
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> ResourceNotFoundException.of("AttendanceSheet", sheetId));
    }
}
