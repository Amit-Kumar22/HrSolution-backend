package com.hrsolution.requisition.service;

import com.hrsolution.audit.entity.AuditAction;
import com.hrsolution.audit.service.AuditService;
import com.hrsolution.catalog.entity.ManpowerCategory;
import com.hrsolution.catalog.repository.ManpowerCategoryRepository;
import com.hrsolution.client.entity.ClientSite;
import com.hrsolution.client.repository.ClientSiteRepository;
import com.hrsolution.client.service.ClientAccessGuard;
import com.hrsolution.common.error.BusinessRuleException;
import com.hrsolution.common.error.ResourceNotFoundException;
import com.hrsolution.common.repository.SpecificationUtils;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.deployment.repository.DeploymentRepository;
import com.hrsolution.requisition.dto.RequisitionDtos;
import com.hrsolution.requisition.entity.Requisition;
import com.hrsolution.requisition.entity.RequisitionStatus;
import com.hrsolution.requisition.mapper.RequisitionMapper;
import com.hrsolution.requisition.repository.RequisitionRepository;
import com.hrsolution.user.entity.User;
import com.hrsolution.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Requisitions - a client's request for workers.
 *
 * <h2>This is the first write a client can make</h2>
 *
 * <p>Everything in the Phase 4 client portal was read-only. A client raising
 * their own requisition changes that, so every entry point here resolves the
 * caller's scope through {@link ClientAccessGuard} exactly as the portal reads
 * do - a client may only raise, see and cancel requisitions for their own
 * company, and a forged {@code clientId} is overridden rather than honoured.
 *
 * <h2>Fulfilment is counted</h2>
 *
 * <p>Every response's {@code quantityDeployed} comes from the deployments
 * table. {@link #refreshFulfilment} is called by {@code DeploymentService} when
 * a posting is created or ended, and it moves the status between
 * {@code IN_PROGRESS}, {@code PARTIALLY_FULFILLED} and {@code FULFILLED}
 * accordingly - so the status follows reality rather than needing a human to
 * remember.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequisitionService {

    /** How soon a start date has to be before the requirement counts as urgent. */
    private static final int URGENT_WINDOW_DAYS = 7;

    private final RequisitionRepository requisitionRepository;
    private final DeploymentRepository deploymentRepository;
    private final ClientSiteRepository siteRepository;
    private final ManpowerCategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final ClientAccessGuard accessGuard;
    private final RequisitionMapper mapper;
    private final AuditService auditService;

    // ==================================================================
    // Reading
    // ==================================================================

    @Transactional(readOnly = true)
    public PageResponse<RequisitionDtos.SummaryResponse> list(Long requestedClientId,
                                                              Long siteId,
                                                              RequisitionStatus status,
                                                              Long categoryId,
                                                              AuthenticatedUser caller,
                                                              Pageable pageable) {
        // For a client user this returns their own id whatever was asked for,
        // so a forged filter cannot widen the result set.
        Long scopedClientId = accessGuard.resolveAccessibleClientId(requestedClientId, caller);

        Specification<Requisition> specification = Specification.<Requisition>unrestricted()
                .and(SpecificationUtils.notDeleted())
                .and(SpecificationUtils.equal("client.id", scopedClientId))
                .and(SpecificationUtils.equal("site.id", siteId))
                .and(SpecificationUtils.equal("status", status))
                .and(SpecificationUtils.equal("category.id", categoryId));

        Page<Requisition> page = requisitionRepository.findAll(specification, pageable);
        Map<Long, Long> deployed = deployedCountsFor(
                page.getContent().stream().map(Requisition::getId).toList());

        return PageResponse.from(page, requisition -> {
            long count = deployed.getOrDefault(requisition.getId(), 0L);
            RequisitionDtos.SummaryResponse base = mapper.toSummaryResponse(requisition);
            return new RequisitionDtos.SummaryResponse(
                    base.id(), base.requisitionNumber(), base.clientName(), base.siteCode(),
                    base.categoryName(), base.quantityRequired(), count,
                    requisition.outstandingFor(count), base.requiredFrom(), base.status(),
                    requisition.isUrgentOn(LocalDate.now(), URGENT_WINDOW_DAYS, count));
        });
    }

    @Transactional(readOnly = true)
    public RequisitionDtos.Response get(Long requisitionId, AuthenticatedUser caller) {
        Requisition requisition = load(requisitionId);
        accessGuard.requireAccessTo(requisition.getClient().getId(), caller);
        return withFulfilment(requisition);
    }

    @Transactional(readOnly = true)
    public RequisitionDtos.DetailResponse getDetail(Long requisitionId, AuthenticatedUser caller) {
        Requisition requisition = requisitionRepository.findActiveWithHistoryById(requisitionId)
                .orElseThrow(() -> ResourceNotFoundException.of("Requisition", requisitionId));
        accessGuard.requireAccessTo(requisition.getClient().getId(), caller);

        List<RequisitionDtos.StatusHistoryEntry> history = requisition.getStatusHistory().stream()
                .map(mapper::toHistoryEntry)
                .toList();

        return new RequisitionDtos.DetailResponse(withFulfilment(requisition), history);
    }

    /** The client portal's own view of a requisition. */
    @Transactional(readOnly = true)
    public RequisitionDtos.ClientResponse getForClient(Long requisitionId,
                                                       AuthenticatedUser caller) {
        Requisition requisition = load(requisitionId);
        accessGuard.requireAccessTo(requisition.getClient().getId(), caller);
        return clientView(requisition);
    }

    @Transactional(readOnly = true)
    public List<RequisitionDtos.ClientResponse> listForClientPortal(AuthenticatedUser caller) {
        Long clientId = accessGuard.resolveAccessibleClientId(null, caller);

        Specification<Requisition> specification = Specification.<Requisition>unrestricted()
                .and(SpecificationUtils.notDeleted())
                .and(SpecificationUtils.equal("client.id", clientId));

        return requisitionRepository.findAll(specification).stream()
                .map(this::clientView)
                .toList();
    }

    /**
     * Committed requirements starting within the window that are not yet
     * filled.
     *
     * <p>The one operations screen that prevents a client being let down on a
     * start date, which is why it exists as its own endpoint rather than a
     * filter combination the user has to remember.
     */
    @Transactional(readOnly = true)
    public List<RequisitionDtos.SummaryResponse> listUrgent(int withinDays) {
        List<Requisition> candidates = requisitionRepository.findCommittedStartingBy(
                LocalDate.now().plusDays(withinDays));
        Map<Long, Long> deployed = deployedCountsFor(
                candidates.stream().map(Requisition::getId).toList());

        return candidates.stream()
                .filter(requisition -> requisition.outstandingFor(
                        deployed.getOrDefault(requisition.getId(), 0L)) > 0)
                .map(requisition -> {
                    long count = deployed.getOrDefault(requisition.getId(), 0L);
                    RequisitionDtos.SummaryResponse base = mapper.toSummaryResponse(requisition);
                    return new RequisitionDtos.SummaryResponse(
                            base.id(), base.requisitionNumber(), base.clientName(),
                            base.siteCode(), base.categoryName(), base.quantityRequired(),
                            count, requisition.outstandingFor(count), base.requiredFrom(),
                            base.status(), true);
                })
                .toList();
    }

    // ==================================================================
    // Writing
    // ==================================================================

    /**
     * Raises a requisition.
     *
     * <p>{@code clientId} is resolved rather than trusted: a client portal user
     * always gets their own, and staff must name one. Always created as
     * {@code DRAFT} - submitting is a separate, recorded act, so a requirement
     * cannot arrive for approval before the person raising it has read it back.
     */
    @Transactional
    public RequisitionDtos.Response create(Long requestedClientId,
                                           RequisitionDtos.Request request,
                                           AuthenticatedUser caller,
                                           RequestContext context) {
        Long clientId = accessGuard.resolveAccessibleClientId(requestedClientId, caller);
        if (clientId == null) {
            throw new BusinessRuleException(
                    "A client is required. Staff must name the client the requisition is for.");
        }

        ClientSite site = loadSite(request.siteId());
        if (!site.getClient().getId().equals(clientId)) {
            throw new BusinessRuleException(
                    ("Site %s belongs to a different client, so a requisition for this client "
                            + "cannot name it.").formatted(site.getSiteCode()));
        }
        if (!site.isActive()) {
            throw new BusinessRuleException(
                    ("Site %s is inactive, so workers cannot be requested for it. Reactivate "
                            + "the site first.").formatted(site.getSiteCode()));
        }

        validateDates(request.requiredFrom(), request.requiredTo());

        Requisition requisition = new Requisition();
        mapper.applyRequest(request, requisition);
        requisition.setClient(site.getClient());
        requisition.setSite(site);
        requisition.setCategory(loadCategory(request.categoryId()));
        requisition.setQuantityRequired(request.quantityRequired());
        requisition.setRaisedBy(resolveUser(caller.id()));
        requisition.setStatus(RequisitionStatus.DRAFT);

        Requisition saved = requisitionRepository.save(requisition);
        saved.recordCreation(caller.id(), caller.email(), "Raised");

        if (site.getStateCode() == null || site.getStateCode().isBlank()) {
            // Not fatal here, but Phase 8 cannot compute a legal wage for
            // anyone deployed against it.
            log.warn("Requisition {} is for site {}, which has no state code. Minimum wage and "
                            + "professional tax cannot be determined for workers deployed here.",
                    saved.getId(), site.getSiteCode());
        }

        auditService.recordEntityChange(AuditAction.CREATE, "Requisition", saved.getId(),
                caller.id(), caller.email(),
                "Raised %s: %d x %s at %s from %s".formatted(
                        saved.requisitionNumber(), saved.getQuantityRequired(),
                        saved.getCategory().getName(), site.getSiteCode(),
                        saved.getRequiredFrom()),
                null, saved.requisitionNumber(), context);

        return withFulfilment(saved);
    }

    /**
     * Amends a requisition.
     *
     * <p>Only while it is a {@code DRAFT} for a client user. Staff may amend a
     * submitted or approved one - a client ringing up to change 20 guards to 25
     * is routine - but not once it is terminal.
     */
    @Transactional
    public RequisitionDtos.Response update(Long requisitionId,
                                           RequisitionDtos.Request request,
                                           AuthenticatedUser caller,
                                           RequestContext context) {
        Requisition requisition = load(requisitionId);
        accessGuard.requireAccessTo(requisition.getClient().getId(), caller);

        if (requisition.getStatus().isTerminal()) {
            throw new BusinessRuleException(
                    ("This requisition is %s, so it can no longer be amended. Raise a new one.")
                            .formatted(requisition.getStatus()));
        }
        if (accessGuard.isClientUser(caller) && !requisition.getStatus().isEditableByClient()) {
            throw new BusinessRuleException(
                    ("This requisition has already been submitted, so it can no longer be "
                            + "edited here. Contact us to change it."));
        }

        ClientSite site = loadSite(request.siteId());
        if (!site.getClient().getId().equals(requisition.getClient().getId())) {
            throw new BusinessRuleException(
                    ("Site %s belongs to a different client.").formatted(site.getSiteCode()));
        }
        validateDates(request.requiredFrom(), request.requiredTo());

        int previousQuantity = requisition.getQuantityRequired();

        mapper.applyRequest(request, requisition);
        requisition.setSite(site);
        requisition.setCategory(loadCategory(request.categoryId()));
        requisition.setQuantityRequired(request.quantityRequired());

        long deployed = deploymentRepository.countActiveForRequisition(requisitionId);
        if (requisition.getQuantityRequired() < deployed) {
            // Allowed - a client really can cut a requirement - but somebody
            // has to release the surplus postings, and that is a decision.
            log.warn("Requisition {} reduced to {} while {} worker(s) are deployed against it. "
                            + "The surplus deployments need releasing.",
                    requisitionId, requisition.getQuantityRequired(), deployed);
        }

        auditService.recordEntityChange(AuditAction.UPDATE, "Requisition", requisitionId,
                caller.id(), caller.email(),
                "Amended " + requisition.requisitionNumber(),
                String.valueOf(previousQuantity),
                String.valueOf(requisition.getQuantityRequired()), context);

        return withFulfilment(requisition);
    }

    /** Moves a requisition through its lifecycle. */
    @Transactional
    public RequisitionDtos.Response changeStatus(Long requisitionId,
                                                 RequisitionDtos.StatusChangeRequest request,
                                                 AuthenticatedUser caller,
                                                 RequestContext context) {
        Requisition requisition = load(requisitionId);
        accessGuard.requireAccessTo(requisition.getClient().getId(), caller);

        RequisitionStatus previous = requisition.getStatus();
        RequisitionStatus target = request.status();

        // A client may submit and cancel their own request. Approving or
        // rejecting is the company's decision, and REQUISITION_APPROVE - which
        // no client role holds - guards the endpoint. This second check stops a
        // client reaching those targets through the shared status endpoint.
        if (accessGuard.isClientUser(caller)
                && target != RequisitionStatus.SUBMITTED
                && target != RequisitionStatus.CANCELLED) {
            throw new BusinessRuleException(
                    "A client can submit or cancel a requisition. Approval and closure are "
                            + "handled by our operations team.");
        }

        if (target == RequisitionStatus.REJECTED
                && (request.remarks() == null || request.remarks().isBlank())) {
            throw new BusinessRuleException(
                    "A reason is required when rejecting a requisition. The client will be "
                            + "told it was refused and will ask why.");
        }

        moveOrExplain(requisition, target, caller, request.remarks());

        if (target == RequisitionStatus.REJECTED) {
            requisition.setRejectionReason(request.remarks());
        }
        if (target == RequisitionStatus.APPROVED) {
            requisition.setApprovedBy(resolveUser(caller.id()));
            requisition.setApprovedAt(Instant.now());
        }
        if (target == RequisitionStatus.CANCELLED) {
            long deployed = deploymentRepository.countActiveForRequisition(requisitionId);
            if (deployed > 0) {
                log.warn("Requisition {} cancelled with {} worker(s) still deployed against it. "
                                + "Those deployments remain active and need releasing.",
                        requisitionId, deployed);
            }
        }

        auditService.recordEntityChange(AuditAction.UPDATE, "Requisition", requisitionId,
                caller.id(), caller.email(),
                "%s moved %s -> %s%s".formatted(requisition.requisitionNumber(), previous, target,
                        request.remarks() == null ? "" : ": " + request.remarks()),
                previous.name(), target.name(), context);

        return withFulfilment(requisition);
    }

    @Transactional
    public void delete(Long requisitionId, AuthenticatedUser caller, RequestContext context) {
        Requisition requisition = load(requisitionId);
        accessGuard.requireAccessTo(requisition.getClient().getId(), caller);

        long deployed = deploymentRepository.countActiveForRequisition(requisitionId);
        if (deployed > 0) {
            throw new BusinessRuleException(
                    ("%d worker(s) are deployed against this requisition, so deleting it would "
                            + "orphan their postings. Cancel or close it instead.")
                            .formatted(deployed));
        }

        requisition.markDeleted(caller.email());

        auditService.recordEntityChange(AuditAction.DELETE, "Requisition", requisitionId,
                caller.id(), caller.email(),
                "Deleted " + requisition.requisitionNumber(), null, null, context);
    }

    // ==================================================================
    // Called by DeploymentService
    // ==================================================================

    /**
     * Re-derives the fulfilment status after a posting is created or ended.
     *
     * <p>Called from {@code DeploymentService} inside the same transaction, so
     * the requisition and the deployment either both move or neither does. A
     * refusal from the state machine is logged rather than thrown: the
     * deployment itself has succeeded and must not roll back because the
     * requisition could not follow.
     */
    @Transactional
    public void refreshFulfilment(Requisition requisition, AuthenticatedUser actor) {
        if (requisition == null) {
            return;
        }
        long deployed = deploymentRepository.countActiveForRequisition(requisition.getId());
        RequisitionStatus target = requisition.fulfilmentStatusFor(deployed);

        if (target == requisition.getStatus()) {
            return;
        }
        if (!requisition.getStatus().canTransitionTo(target)) {
            log.warn("Requisition {} is {} with {} of {} deployed, which implies {}, but that "
                            + "transition is not permitted. The deployment stands and the "
                            + "requisition status is unchanged.",
                    requisition.getId(), requisition.getStatus(), deployed,
                    requisition.getQuantityRequired(), target);
            return;
        }

        requisition.moveTo(target, actor == null ? null : actor.id(),
                actor == null ? null : actor.email(),
                "Fulfilment now %d of %d".formatted(deployed, requisition.getQuantityRequired()));
    }

    /** Loads a requisition for {@code DeploymentService}, with the access check. */
    @Transactional(readOnly = true)
    public Requisition loadAccessible(Long requisitionId, AuthenticatedUser caller) {
        Requisition requisition = load(requisitionId);
        accessGuard.requireAccessTo(requisition.getClient().getId(), caller);
        return requisition;
    }

    // ==================================================================
    // Internals
    // ==================================================================

    private Requisition load(Long requisitionId) {
        return requisitionRepository.findActiveById(requisitionId)
                .orElseThrow(() -> ResourceNotFoundException.of("Requisition", requisitionId));
    }

    private ClientSite loadSite(Long siteId) {
        return siteRepository.findActiveById(siteId)
                .orElseThrow(() -> ResourceNotFoundException.of("Site", siteId));
    }

    private ManpowerCategory loadCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> ResourceNotFoundException.of("Manpower category", categoryId));
    }

    private User resolveUser(Long userId) {
        return userId == null ? null : userRepository.findById(userId).orElse(null);
    }

    private void validateDates(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new BusinessRuleException(
                    "The end date is before the start date. Swap them, or leave the end blank "
                            + "for an ongoing requirement.");
        }
    }

    private void moveOrExplain(Requisition requisition, RequisitionStatus target,
                               AuthenticatedUser caller, String remarks) {
        try {
            requisition.moveTo(target, caller.id(), caller.email(), remarks);
        } catch (IllegalStateException e) {
            RequisitionStatus current = requisition.getStatus();
            throw new BusinessRuleException(current.isTerminal()
                    ? ("This requisition is %s, which is final. Raise a new one to revive the "
                            + "requirement.").formatted(current)
                    : ("A requisition cannot move from %s to %s. From here it can go to: %s.")
                            .formatted(current, target, current.allowedNextStatuses()));
        }
    }

    private RequisitionDtos.Response withFulfilment(Requisition requisition) {
        long deployed = deploymentRepository.countActiveForRequisition(requisition.getId());
        RequisitionDtos.Response base = mapper.toResponse(requisition);

        return new RequisitionDtos.Response(
                base.id(), base.requisitionNumber(),
                base.clientId(), base.clientName(),
                base.siteId(), base.siteCode(), base.siteName(), base.siteStateCode(),
                base.categoryId(), base.categoryName(),
                base.effectiveSkillLevel(), base.skillLevel(),
                base.quantityRequired(), deployed, requisition.outstandingFor(deployed),
                base.shift(), base.requiredFrom(), base.requiredTo(),
                base.status(), base.allowedNextStatuses(), base.terminal(),
                base.acceptsDeployments(),
                requisition.isUrgentOn(LocalDate.now(), URGENT_WINDOW_DAYS, deployed),
                base.raisedByUserId(), base.raisedByName(),
                base.approvedByUserId(), base.approvedByName(), base.approvedAt(),
                base.rejectionReason(), base.notes(), base.createdAt());
    }

    private RequisitionDtos.ClientResponse clientView(Requisition requisition) {
        long deployed = deploymentRepository.countActiveForRequisition(requisition.getId());
        RequisitionDtos.ClientResponse base = mapper.toClientResponse(requisition);

        return new RequisitionDtos.ClientResponse(
                base.id(), base.requisitionNumber(),
                base.siteId(), base.siteCode(), base.siteName(),
                base.categoryName(), base.skillLevel(),
                base.quantityRequired(), deployed, requisition.outstandingFor(deployed),
                base.shift(), base.requiredFrom(), base.requiredTo(),
                base.status(), base.statusLabel(), base.terminal(), base.editable(),
                base.createdAt());
    }

    private Map<Long, Long> deployedCountsFor(List<Long> requisitionIds) {
        if (requisitionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (DeploymentRepository.RequisitionDeploymentCount row
                : deploymentRepository.countActiveForRequisitions(requisitionIds)) {
            counts.put(row.getRequisitionId(), row.getTotal());
        }
        return counts;
    }
}
