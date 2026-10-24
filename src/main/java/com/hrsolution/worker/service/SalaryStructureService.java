package com.hrsolution.worker.service;

import com.hrsolution.audit.entity.AuditAction;
import com.hrsolution.audit.service.AuditService;
import com.hrsolution.common.error.BusinessRuleException;
import com.hrsolution.common.error.ResourceNotFoundException;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.util.MoneyUtils;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.worker.dto.SalaryStructureDtos;
import com.hrsolution.worker.entity.SalaryStructure;
import com.hrsolution.worker.entity.Worker;
import com.hrsolution.worker.mapper.WorkerMapper;
import com.hrsolution.worker.repository.SalaryStructureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Wage structures as dated history.
 *
 * <p>Modelled on {@code RateCardService} from Phase 4, and for the same
 * reason: recomputing an earlier month's payroll must use the structure that
 * applied then. A revision inserts a row and closes its predecessor the day
 * before; nothing is ever edited in place once it has taken effect.
 *
 * <p>The consequence, which surprises people, is that
 * {@link #supersede} is the <em>only</em> way to change a wage. There is no
 * update endpoint for a live structure, and {@link #correct} works only on one
 * that has not started yet.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalaryStructureService {

    private final SalaryStructureRepository structureRepository;
    private final WorkerService workerService;
    private final WorkerMapper mapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<SalaryStructureDtos.Response> historyForWorker(Long workerId) {
        workerService.loadActive(workerId);
        return structureRepository.findHistoryForWorker(workerId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    /** The structure in force on a date - what Phase 8 calls. */
    @Transactional(readOnly = true)
    public SalaryStructureDtos.Response effectiveOn(Long workerId, LocalDate onDate) {
        workerService.loadActive(workerId);
        LocalDate date = onDate == null ? LocalDate.now() : onDate;

        return structureRepository.findEffectiveOn(workerId, date)
                .map(mapper::toResponse)
                .orElseThrow(() -> new BusinessRuleException(
                        ("This worker has no wage structure in force on %s, so their pay for "
                                + "that period cannot be computed.").formatted(date)));
    }

    /**
     * Creates a structure, closing the current one the day before it starts.
     *
     * <p>Refuses a start date on or before the current structure's own start:
     * that would leave two rows claiming the same day, and
     * {@code findEffectiveOn} would return whichever the database felt like.
     */
    @Transactional
    public SalaryStructureDtos.Response supersede(Long workerId,
                                                  SalaryStructureDtos.Request request,
                                                  AuthenticatedUser actor,
                                                  RequestContext context) {
        Worker worker = workerService.loadActive(workerId);

        if (worker.getStatus().hasExited()) {
            throw new BusinessRuleException(
                    ("This worker left on %s, so a new wage structure cannot start. Their final "
                            + "settlement uses the structure in force on their last day.")
                            .formatted(worker.getLastWorkingDay()));
        }
        if (request.effectiveFrom().isBefore(worker.getDateOfJoining())) {
            throw new BusinessRuleException(
                    ("A wage structure cannot start before the worker joined on %s.")
                            .formatted(worker.getDateOfJoining()));
        }

        SalaryStructure current = structureRepository.findCurrentForWorker(workerId).orElse(null);
        if (current != null) {
            if (!request.effectiveFrom().isAfter(current.getEffectiveFrom())) {
                throw new BusinessRuleException(
                        ("The current structure starts on %s, so a successor must start after "
                                + "that. Two structures covering the same day would make the "
                                + "applicable wage ambiguous.")
                                .formatted(current.getEffectiveFrom()));
            }
            current.closeBefore(request.effectiveFrom());
            log.info("Closed wage structure {} for worker {} at {} ahead of its successor",
                    current.getId(), worker.employeeCode(), current.getEffectiveTo());
        }

        SalaryStructure structure = build(request, worker);
        SalaryStructure saved = structureRepository.save(structure);

        warnIfImplausible(saved, worker);

        auditService.recordEntityChange(AuditAction.CREATE, "SalaryStructure", saved.getId(),
                actor.id(), actor.email(),
                "Wage structure for %s effective %s: gross %s".formatted(
                        worker.employeeCode(), saved.getEffectiveFrom(), saved.gross()),
                current == null ? null : current.gross().toPlainString(),
                saved.gross().toPlainString(), context);

        return mapper.toResponse(saved);
    }

    /**
     * Corrects a structure that has not taken effect yet.
     *
     * <p>Refused once it is live: payslips have been issued against it, and a
     * silent restatement of a past month is how payroll disputes start.
     */
    @Transactional
    public SalaryStructureDtos.Response correct(Long structureId,
                                                SalaryStructureDtos.Request request,
                                                AuthenticatedUser actor,
                                                RequestContext context) {
        SalaryStructure structure = load(structureId);

        if (!structure.getEffectiveFrom().isAfter(LocalDate.now())) {
            throw new BusinessRuleException(
                    ("This structure took effect on %s and may already have been paid against. "
                            + "Create a new structure effective from a future date instead of "
                            + "editing this one.").formatted(structure.getEffectiveFrom()));
        }

        BigDecimal previousGross = structure.gross();

        mapper.applyRequest(request, structure);
        applyComponents(structure, request);

        auditService.recordEntityChange(AuditAction.UPDATE, "SalaryStructure", structureId,
                actor.id(), actor.email(),
                "Corrected the unstarted wage structure for "
                        + structure.getWorker().employeeCode(),
                previousGross.toPlainString(), structure.gross().toPlainString(), context);

        return mapper.toResponse(structure);
    }

    @Transactional
    public void delete(Long structureId, AuthenticatedUser actor, RequestContext context) {
        SalaryStructure structure = load(structureId);

        if (!structure.getEffectiveFrom().isAfter(LocalDate.now())) {
            throw new BusinessRuleException(
                    "A wage structure that has taken effect cannot be deleted - it is the basis "
                            + "of payslips already issued.");
        }

        structure.markDeleted(actor.email());

        // Reopen the predecessor, otherwise the worker is left with a gap and
        // no structure in force from the deleted one's start date onwards.
        structureRepository.findHistoryForWorker(structure.getWorker().getId()).stream()
                .filter(other -> !other.getId().equals(structureId))
                .filter(other -> structure.getEffectiveFrom().minusDays(1)
                        .equals(other.getEffectiveTo()))
                .findFirst()
                .ifPresent(predecessor -> {
                    predecessor.setEffectiveTo(null);
                    log.info("Reopened wage structure {} after deleting its unstarted successor",
                            predecessor.getId());
                });

        auditService.recordEntityChange(AuditAction.DELETE, "SalaryStructure", structureId,
                actor.id(), actor.email(),
                "Deleted the unstarted wage structure for "
                        + structure.getWorker().employeeCode(), null, null, context);
    }

    // ------------------------------------------------------------------

    private SalaryStructure build(SalaryStructureDtos.Request request, Worker worker) {
        SalaryStructure structure = new SalaryStructure();
        mapper.applyRequest(request, structure);
        structure.setWorker(worker);
        applyComponents(structure, request);
        return structure;
    }

    /** Nulls become zero, so the gross is always the sum of real numbers. */
    private void applyComponents(SalaryStructure structure, SalaryStructureDtos.Request request) {
        structure.setBasic(nz(request.basic()));
        structure.setDa(nz(request.da()));
        structure.setHra(nz(request.hra()));
        structure.setConveyance(nz(request.conveyance()));
        structure.setOtherAllowance(nz(request.otherAllowance()));
    }

    /**
     * Flags a structure that is probably a data-entry error.
     *
     * <p>Not refused - a trainee on a stipend is legitimate, and the statutory
     * minimum varies by state and skill level in ways this phase does not model.
     * Phase 8 validates against the real notified rates. A warning now means
     * somebody can catch a missing zero before payroll runs.
     */
    private void warnIfImplausible(SalaryStructure structure, Worker worker) {
        if (structure.gross().compareTo(IMPLAUSIBLY_LOW_GROSS) < 0) {
            log.warn("Wage structure {} for worker {} has a gross of {}, which is below any "
                            + "state minimum wage. Check for a missing digit.",
                    structure.getId(), worker.employeeCode(), structure.gross());
        }
        if (structure.getBasic().signum() == 0) {
            log.warn("Wage structure {} for worker {} has a zero basic. PF is computed on "
                            + "basic + DA, so this will under-deduct.",
                    structure.getId(), worker.employeeCode());
        }
    }

    private SalaryStructure load(Long structureId) {
        return structureRepository.findActiveById(structureId)
                .orElseThrow(() -> ResourceNotFoundException.of("Wage structure", structureId));
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? MoneyUtils.ZERO : value;
    }

    /** Below any state's monthly minimum wage, so almost certainly a typo. */
    private static final BigDecimal IMPLAUSIBLY_LOW_GROSS = new BigDecimal("5000");
}
