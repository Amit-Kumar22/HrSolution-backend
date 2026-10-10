package com.hrsolution.client.service;

import com.hrsolution.audit.entity.AuditAction;
import com.hrsolution.audit.service.AuditService;
import com.hrsolution.client.dto.ClientContractDtos;
import com.hrsolution.client.entity.Client;
import com.hrsolution.client.entity.ClientContract;
import com.hrsolution.client.entity.ContractStatus;
import com.hrsolution.client.entity.ServiceChargeType;
import com.hrsolution.client.mapper.ClientMapper;
import com.hrsolution.client.repository.ClientContractRepository;
import com.hrsolution.client.repository.ClientRepository;
import com.hrsolution.common.error.BusinessRuleException;
import com.hrsolution.common.error.DuplicateResourceException;
import com.hrsolution.common.error.FieldValidationException;
import com.hrsolution.common.error.ResourceNotFoundException;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.storage.FileTypeDetector;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.document.entity.DocumentOwnerType;
import com.hrsolution.document.entity.StoredDocument;
import com.hrsolution.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Client contracts - the commercial terms Phase 9 bills against.
 *
 * <p>Two rules carry the weight here.
 *
 * <p><strong>Live contracts for one client may not overlap.</strong> Two active
 * contracts covering the same day would mean two different service charges
 * could apply to the same invoice, and whichever the query returned first would
 * win. That is a silent revenue error, so it is refused at the point of entry.
 *
 * <p><strong>An active contract's commercial terms are frozen.</strong> Once a
 * contract is in force it may have been billed against; changing the service
 * charge retroactively would make an issued invoice unreproducible. Terms are
 * editable in DRAFT, and after that the route is to terminate and raise a
 * successor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientContractService {

    /**
     * Sanity ceiling on a percentage service charge. Real deals in this
     * industry sit in the 5-15% range; anything above this is a decimal point
     * in the wrong place, which would otherwise be billed.
     */
    private static final BigDecimal MAX_SERVICE_CHARGE_PERCENT = new BigDecimal("100");

    private final ClientContractRepository contractRepository;
    private final ClientRepository clientRepository;
    private final ClientMapper clientMapper;
    private final ClientAccessGuard accessGuard;
    private final DocumentService documentService;
    private final AuditService auditService;

    // ==================================================================
    // Queries
    // ==================================================================

    @Transactional(readOnly = true)
    public List<ClientContractDtos.Response> listForClient(Long clientId,
                                                           AuthenticatedUser caller) {
        accessGuard.requireAccessTo(clientId, caller);
        return contractRepository.findByClientId(clientId).stream()
                .map(this::withDocumentCount)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClientContractDtos.Response get(Long contractId, AuthenticatedUser caller) {
        ClientContract contract = load(contractId);
        accessGuard.requireAccessTo(contract.getClient().getId(), caller);
        return withDocumentCount(contract);
    }

    /**
     * The contract to bill against on a date.
     *
     * <p>Returns the single contract in force, or fails loudly when more than
     * one is - which should be impossible given the overlap check on create,
     * but is worth detecting rather than picking one arbitrarily if data was
     * ever loaded around the API.
     */
    @Transactional(readOnly = true)
    public ClientContractDtos.Response findInForceOn(Long clientId,
                                                     LocalDate onDate,
                                                     AuthenticatedUser caller) {
        accessGuard.requireAccessTo(clientId, caller);
        LocalDate date = onDate == null ? LocalDate.now() : onDate;

        List<ClientContract> inForce = contractRepository.findInForceOn(clientId, date);

        if (inForce.isEmpty()) {
            throw new ResourceNotFoundException(
                    ("No active contract covers %s for this client. Billing for that period "
                            + "cannot compute a service charge until one exists.").formatted(date));
        }
        if (inForce.size() > 1) {
            log.error("Client {} has {} active contracts covering {}: {}. Billing is ambiguous.",
                    clientId, inForce.size(), date,
                    inForce.stream().map(ClientContract::getContractNumber).toList());
            throw new BusinessRuleException(
                    ("%d active contracts cover %s for this client. Exactly one must apply, or "
                            + "the service charge on an invoice is ambiguous. Terminate or "
                            + "re-date the extras.").formatted(inForce.size(), date));
        }
        return withDocumentCount(inForce.getFirst());
    }

    // ==================================================================
    // Create and update
    // ==================================================================

    @Transactional
    public ClientContractDtos.Response create(Long clientId,
                                              ClientContractDtos.Request request,
                                              AuthenticatedUser actor,
                                              RequestContext context) {
        Client client = clientRepository.findActiveById(clientId)
                .orElseThrow(() -> ResourceNotFoundException.of("Client", clientId));

        if (!client.allowsNewWork()) {
            throw new BusinessRuleException(
                    ("This client is %s, so a new contract cannot be raised.")
                            .formatted(client.getStatus()));
        }

        validateServiceCharge(request.serviceChargeType(), request.serviceChargeValue());
        validateDates(request.startDate(), request.endDate());

        String contractNumber = request.contractNumber().trim();
        if (contractRepository.existsByContractNumberIgnoreCase(contractNumber)) {
            throw DuplicateResourceException.of("A contract", "number", contractNumber);
        }

        ClientContract contract = new ClientContract();
        clientMapper.applyRequest(request, contract);
        contract.setClient(client);
        contract.setContractNumber(contractNumber);
        // DRAFT, not ACTIVE. Activating is a separate, deliberate step, because
        // activation is what makes the terms binding and freezes them.
        contract.setStatus(ContractStatus.DRAFT);

        ClientContract saved = contractRepository.save(contract);

        auditService.recordEntityChange(AuditAction.CREATE, "ClientContract", saved.getId(),
                actor.id(), actor.email(),
                "Drafted contract %s for %s: %s".formatted(
                        saved.getContractNumber(), client.clientCode(),
                        clientMapper.serviceChargeLabel(
                                saved.getServiceChargeType(), saved.getServiceChargeValue())),
                null, saved.getContractNumber(), context);

        return withDocumentCount(saved);
    }

    @Transactional
    public ClientContractDtos.Response update(Long contractId,
                                              ClientContractDtos.Request request,
                                              AuthenticatedUser actor,
                                              RequestContext context) {
        ClientContract contract = load(contractId);

        // The commercial terms of a live contract are frozen: it may already
        // have been billed against, and changing the service charge now would
        // make an issued invoice impossible to reproduce.
        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new BusinessRuleException(
                    ("Contract %s is %s, so its terms can no longer be edited - invoices may "
                            + "already have been raised against them. Terminate it and raise a "
                            + "successor instead.")
                            .formatted(contract.getContractNumber(), contract.getStatus()));
        }

        validateServiceCharge(request.serviceChargeType(), request.serviceChargeValue());
        validateDates(request.startDate(), request.endDate());

        String newNumber = request.contractNumber().trim();
        if (!contract.getContractNumber().equalsIgnoreCase(newNumber)
                && contractRepository.existsByContractNumberIgnoreCase(newNumber)) {
            throw DuplicateResourceException.of("A contract", "number", newNumber);
        }

        clientMapper.applyRequest(request, contract);
        contract.setContractNumber(newNumber);

        auditService.recordEntityChange(AuditAction.UPDATE, "ClientContract", contractId,
                actor.id(), actor.email(),
                "Updated draft contract " + contract.getContractNumber(), null, null, context);

        return withDocumentCount(contract);
    }

    /**
     * Activates a draft contract, making its terms binding.
     *
     * <p>The overlap check happens here rather than at create, so that a draft
     * can be prepared alongside a running contract and only conflict when
     * somebody tries to make both live.
     */
    @Transactional
    public ClientContractDtos.Response activate(Long contractId,
                                                AuthenticatedUser actor,
                                                RequestContext context) {
        ClientContract contract = load(contractId);

        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new BusinessRuleException(
                    "Only a DRAFT contract can be activated; this one is "
                            + contract.getStatus() + ".");
        }

        rejectOverlappingActiveContracts(contract);

        contract.setStatus(ContractStatus.ACTIVE);

        auditService.recordEntityChange(AuditAction.UPDATE, "ClientContract", contractId,
                actor.id(), actor.email(),
                "Activated contract %s (%s to %s)".formatted(
                        contract.getContractNumber(), contract.getStartDate(),
                        contract.getEndDate() == null ? "open-ended" : contract.getEndDate()),
                ContractStatus.DRAFT.name(), ContractStatus.ACTIVE.name(), context);

        log.info("Contract {} for client {} activated: {}",
                contract.getContractNumber(), contract.getClient().clientCode(),
                clientMapper.serviceChargeLabel(
                        contract.getServiceChargeType(), contract.getServiceChargeValue()));

        return withDocumentCount(contract);
    }

    @Transactional
    public ClientContractDtos.Response terminate(Long contractId,
                                                 ClientContractDtos.TerminateRequest request,
                                                 AuthenticatedUser actor,
                                                 RequestContext context) {
        ClientContract contract = load(contractId);

        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "Only an ACTIVE contract can be terminated; this one is "
                            + contract.getStatus() + ".");
        }
        if (request.terminatedOn().isBefore(contract.getStartDate())) {
            throw FieldValidationException.of("terminatedOn",
                    "cannot be before the contract start date of " + contract.getStartDate());
        }

        contract.setStatus(ContractStatus.TERMINATED);
        contract.setTerminatedOn(request.terminatedOn());
        contract.setTerminationReason(request.reason());
        // The termination date becomes the effective end, so billing after it
        // finds no contract in force rather than silently using the old terms.
        if (contract.getEndDate() == null || contract.getEndDate().isAfter(request.terminatedOn())) {
            contract.setEndDate(request.terminatedOn());
        }

        auditService.recordEntityChange(AuditAction.UPDATE, "ClientContract", contractId,
                actor.id(), actor.email(),
                "Terminated contract %s on %s: %s".formatted(
                        contract.getContractNumber(), request.terminatedOn(), request.reason()),
                ContractStatus.ACTIVE.name(), ContractStatus.TERMINATED.name(), context);

        log.info("Contract {} terminated on {} by {}: {}",
                contract.getContractNumber(), request.terminatedOn(), actor.email(),
                request.reason());

        return withDocumentCount(contract);
    }

    /** Uploads the signed contract document. Images or PDF, up to 10 MB. */
    @Transactional
    public ClientContractDtos.Response uploadDocument(Long contractId,
                                                      MultipartFile file,
                                                      AuthenticatedUser actor,
                                                      RequestContext context) {
        ClientContract contract = load(contractId);

        StoredDocument document = documentService.upload(
                file, DocumentOwnerType.CONTRACT, contractId, FileTypeDetector.IMAGES_AND_PDF);

        auditService.recordEntityChange(AuditAction.UPDATE, "ClientContract", contractId,
                actor.id(), actor.email(),
                "Attached document '%s' to contract %s".formatted(
                        document.getOriginalFileName(), contract.getContractNumber()),
                null, document.getStorageKey(), context);

        return withDocumentCount(contract);
    }

    @Transactional
    public void delete(Long contractId, AuthenticatedUser actor, RequestContext context) {
        ClientContract contract = load(contractId);

        if (contract.getStatus() == ContractStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "Terminate this contract before deleting it - an active contract may be "
                            + "carrying the billing terms for the current month.");
        }

        contract.markDeleted(actor.email());

        auditService.recordEntityChange(AuditAction.DELETE, "ClientContract", contractId,
                actor.id(), actor.email(),
                "Deleted contract " + contract.getContractNumber(), null, null, context);
    }

    // ==================================================================
    // Validation
    // ==================================================================

    /**
     * Bounds the service charge according to its type.
     *
     * <p>A single annotation cannot do this: {@code 150} is nonsense as a
     * percentage but a perfectly ordinary per-worker fee in rupees.
     */
    private void validateServiceCharge(ServiceChargeType type, BigDecimal value) {
        if (type == ServiceChargeType.PERCENTAGE
                && value.compareTo(MAX_SERVICE_CHARGE_PERCENT) > 0) {
            throw FieldValidationException.of("serviceChargeValue",
                    "a percentage service charge cannot exceed 100%. Did you mean "
                            + "FIXED_PER_WORKER?");
        }
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (endDate != null && endDate.isBefore(startDate)) {
            throw FieldValidationException.of("endDate",
                    "must be on or after the start date");
        }
    }

    /**
     * Refuses activation when another live contract covers any of the same days.
     *
     * <p>Uses the shared {@code DateUtils.overlaps} through
     * {@code ClientContract.overlaps}, which treats a null end date as
     * open-ended - so an existing open-ended contract blocks every later one,
     * which is correct: it has no end, so nothing can follow it until it is
     * given one or terminated.
     */
    private void rejectOverlappingActiveContracts(ClientContract candidate) {
        List<ClientContract> live = contractRepository.findLiveForOverlapCheck(
                candidate.getClient().getId(), candidate.getId());

        List<ClientContract> conflicts = live.stream()
                .filter(existing -> existing.getStatus() == ContractStatus.ACTIVE)
                .filter(candidate::overlaps)
                .toList();

        if (!conflicts.isEmpty()) {
            ClientContract first = conflicts.getFirst();
            throw new BusinessRuleException(
                    ("Contract %s (%s to %s) is already active and overlaps these dates. Two "
                            + "active contracts covering the same day would make the service "
                            + "charge on an invoice ambiguous - terminate or re-date one of them.")
                            .formatted(first.getContractNumber(), first.getStartDate(),
                                    first.getEndDate() == null ? "open-ended" : first.getEndDate()));
        }
    }

    // ==================================================================

    private ClientContract load(Long contractId) {
        return contractRepository.findActiveById(contractId)
                .orElseThrow(() -> ResourceNotFoundException.of("Contract", contractId));
    }

    /** Fills in the document count the mapper leaves out. */
    private ClientContractDtos.Response withDocumentCount(ClientContract contract) {
        ClientContractDtos.Response base = clientMapper.toResponse(contract);
        int documents = documentService
                .listForOwner(DocumentOwnerType.CONTRACT, contract.getId()).size();

        return new ClientContractDtos.Response(
                base.id(), base.clientId(), base.clientName(), base.contractNumber(),
                base.title(), base.startDate(), base.endDate(), base.serviceChargeType(),
                base.serviceChargeValue(), base.serviceChargeLabel(), base.paymentTermsDays(),
                base.effectivePaymentTermsDays(), base.status(), base.inForceToday(),
                base.daysUntilExpiry(), base.terminatedOn(), base.terminationReason(),
                base.notes(), documents);
    }
}
