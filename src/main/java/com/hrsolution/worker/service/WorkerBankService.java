package com.hrsolution.worker.service;

import com.hrsolution.audit.entity.AuditAction;
import com.hrsolution.audit.service.AuditService;
import com.hrsolution.common.crypto.FieldCrypto;
import com.hrsolution.common.error.BusinessRuleException;
import com.hrsolution.common.error.DuplicateResourceException;
import com.hrsolution.common.error.ResourceNotFoundException;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.worker.dto.WorkerBankDtos;
import com.hrsolution.worker.entity.Worker;
import com.hrsolution.worker.entity.WorkerBankDetail;
import com.hrsolution.worker.mapper.WorkerMapper;
import com.hrsolution.worker.repository.WorkerBankDetailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * A worker's bank accounts.
 *
 * <h2>Exactly one primary</h2>
 *
 * <p>Wages are paid into the primary account, so "which one" must never be
 * ambiguous and must never be absent while the worker has any account at all.
 * MySQL has no partial unique index, so the rule lives here:
 *
 * <ul>
 *   <li>the first account added becomes primary automatically, because an
 *       account nobody marked is still the only one there is;</li>
 *   <li>promoting one demotes the rest in a single bulk statement, so there is
 *       no window with none - a concurrent payroll run reading through that
 *       window would find no account to pay into;</li>
 *   <li>the primary account cannot be deactivated or deleted while another
 *       account exists to take over, without first promoting that other one.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkerBankService {

    private final WorkerBankDetailRepository bankRepository;
    private final WorkerService workerService;
    private final FieldCrypto fieldCrypto;
    private final WorkerMapper mapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<WorkerBankDtos.Response> listForWorker(Long workerId) {
        // Confirms the worker exists, so an unknown id is a 404 rather than a
        // misleading empty list.
        workerService.loadActive(workerId);
        return bankRepository.findForWorker(workerId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional
    public WorkerBankDtos.Response add(Long workerId,
                                       WorkerBankDtos.Request request,
                                       AuthenticatedUser actor,
                                       RequestContext context) {
        Worker worker = workerService.loadActive(workerId);

        String hash = fieldCrypto.blindIndex(request.accountNumber());
        if (bankRepository.existsForWorkerAndAccountHash(workerId, hash)) {
            // On the blind index, because the ciphertext cannot be compared.
            throw new DuplicateResourceException(
                    "This account is already registered for this worker.");
        }

        WorkerBankDetail account = new WorkerBankDetail();
        mapper.applyRequest(request, account);
        account.setWorker(worker);
        account.setAccountNumber(request.accountNumber().trim());
        account.setAccountNumberHash(hash);
        account.setIfsc(request.ifsc().trim().toUpperCase());
        account.setActive(true);
        // Defaults to the worker's own name; a mismatch with what the bank
        // holds is what makes a transfer file bounce.
        if (request.accountHolderName() == null || request.accountHolderName().isBlank()) {
            account.setAccountHolderName(worker.fullName());
        }

        boolean isFirst = bankRepository.findForWorker(workerId).isEmpty();
        boolean wantsPrimary = Boolean.TRUE.equals(request.primaryAccount()) || isFirst;

        WorkerBankDetail saved = bankRepository.save(account);
        if (wantsPrimary) {
            promote(saved);
        }

        auditService.recordEntityChange(AuditAction.CREATE, "WorkerBankDetail", saved.getId(),
                actor.id(), actor.email(),
                "Added bank account %s for %s%s".formatted(
                        saved.maskedAccountNumber(), worker.employeeCode(),
                        wantsPrimary ? " (primary)" : ""),
                null, saved.getIfsc(), context);

        return mapper.toResponse(saved);
    }

    /** Makes one account the primary, demoting the others atomically. */
    @Transactional
    public WorkerBankDtos.Response makePrimary(Long accountId,
                                               AuthenticatedUser actor,
                                               RequestContext context) {
        WorkerBankDetail account = load(accountId);

        if (!account.isActive()) {
            throw new BusinessRuleException(
                    "An inactive account cannot be made primary. Reactivate it first.");
        }

        promote(account);

        auditService.recordEntityChange(AuditAction.UPDATE, "WorkerBankDetail", accountId,
                actor.id(), actor.email(),
                "Made %s the primary account for %s".formatted(
                        account.maskedAccountNumber(), account.getWorker().employeeCode()),
                null, "primary", context);

        return mapper.toResponse(account);
    }

    @Transactional
    public WorkerBankDtos.Response setActive(Long accountId,
                                             boolean active,
                                             AuthenticatedUser actor,
                                             RequestContext context) {
        WorkerBankDetail account = load(accountId);

        if (!active && account.isPrimaryAccount()) {
            throw new BusinessRuleException(
                    "This is the account wages are paid into. Make another account primary "
                            + "before deactivating it, or the next payroll run has nowhere to "
                            + "pay.");
        }

        account.setActive(active);

        auditService.recordEntityChange(AuditAction.UPDATE, "WorkerBankDetail", accountId,
                actor.id(), actor.email(),
                "%s bank account %s for %s".formatted(
                        active ? "Activated" : "Deactivated", account.maskedAccountNumber(),
                        account.getWorker().employeeCode()),
                null, String.valueOf(active), context);

        return mapper.toResponse(account);
    }

    @Transactional
    public void delete(Long accountId, AuthenticatedUser actor, RequestContext context) {
        WorkerBankDetail account = load(accountId);
        Long workerId = account.getWorker().getId();

        if (account.isPrimaryAccount()) {
            boolean othersExist = bankRepository.findForWorker(workerId).stream()
                    .anyMatch(other -> !other.getId().equals(accountId));
            if (othersExist) {
                throw new BusinessRuleException(
                        "This is the primary account. Make another account primary first, so "
                                + "there is always exactly one.");
            }
        }

        account.markDeleted(actor.email());
        account.setActive(false);
        account.setPrimaryAccount(false);

        auditService.recordEntityChange(AuditAction.DELETE, "WorkerBankDetail", accountId,
                actor.id(), actor.email(),
                "Removed bank account %s from %s".formatted(
                        account.maskedAccountNumber(), account.getWorker().employeeCode()),
                null, null, context);
    }

    /**
     * The account Phase 8 pays into, for one worker.
     *
     * <p>Returns the entity rather than a DTO: the caller needs the decrypted
     * account number to build a transfer file, and no response shape exposes
     * it.
     */
    @Transactional(readOnly = true)
    public WorkerBankDetail requirePayableAccount(Long workerId) {
        WorkerBankDetail primary = bankRepository.findPrimaryForWorker(workerId)
                .orElseThrow(() -> new BusinessRuleException(
                        "This worker has no primary bank account, so wages cannot be paid. Add "
                                + "one before running payroll."));
        if (!primary.isPayable()) {
            throw new BusinessRuleException(
                    "This worker's primary bank account is inactive or incomplete.");
        }
        return primary;
    }

    // ------------------------------------------------------------------

    /**
     * Demotes every other account, then promotes this one.
     *
     * <p>Order matters: clearing first and setting second means the invariant
     * is briefly violated inside this transaction but never outside it. Doing
     * it the other way round would have the bulk update clear the flag that was
     * just set.
     */
    private void promote(WorkerBankDetail account) {
        bankRepository.clearPrimaryForWorker(account.getWorker().getId());
        // The bulk update cleared the persistence context, so re-attach.
        WorkerBankDetail reloaded = load(account.getId());
        reloaded.setPrimaryAccount(true);
        account.setPrimaryAccount(true);
    }

    private WorkerBankDetail load(Long accountId) {
        return bankRepository.findActiveById(accountId)
                .orElseThrow(() -> ResourceNotFoundException.of("Bank account", accountId));
    }
}
