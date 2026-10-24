package com.hrsolution.deployment.service;

import com.hrsolution.audit.entity.AuditAction;
import com.hrsolution.audit.service.AuditService;
import com.hrsolution.catalog.entity.ManpowerCategory;
import com.hrsolution.catalog.repository.ManpowerCategoryRepository;
import com.hrsolution.client.entity.ClientSite;
import com.hrsolution.client.repository.ClientSiteRepository;
import com.hrsolution.client.service.ClientAccessGuard;
import com.hrsolution.client.dto.RateCardDtos;
import com.hrsolution.client.service.RateCardService;
import com.hrsolution.common.error.BusinessRuleException;
import com.hrsolution.common.error.ResourceNotFoundException;
import com.hrsolution.common.repository.SpecificationUtils;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.deployment.dto.DeploymentDtos;
import com.hrsolution.deployment.entity.Deployment;
import com.hrsolution.deployment.entity.DeploymentStatus;
import com.hrsolution.deployment.mapper.DeploymentMapper;
import com.hrsolution.deployment.repository.DeploymentRepository;
import com.hrsolution.requisition.entity.Requisition;
import com.hrsolution.requisition.service.RequisitionService;
import com.hrsolution.worker.entity.Worker;
import com.hrsolution.worker.entity.WorkerDocumentType;
import com.hrsolution.worker.service.WorkerDocumentService;
import com.hrsolution.worker.service.WorkerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Deployments - posting a worker to a client site.
 *
 * <p>This service is where the four modules built so far meet: a worker
 * (Phase 6) is posted to a client site (Phase 4) against a requisition
 * (Phase 6) at the rate from a rate card (Phase 4). Four invariants hold it
 * together.
 *
 * <ol>
 *   <li><strong>No overlapping postings.</strong> A worker cannot be in two
 *       places at once. Enforced by {@link #requireNoOverlap}, which names the
 *       conflicting posting rather than just refusing.</li>
 *   <li><strong>Security roles need police verification.</strong> Under the
 *       Private Security Agencies (Regulation) Act a guard may not be deployed
 *       without it. {@link #requireDeployable} refuses, because the client's
 *       licence and their own liability depend on it.</li>
 *   <li><strong>The rate is snapshotted.</strong> Resolved from the rate card
 *       applicable on the start date and copied onto the row, so a later rate
 *       revision cannot restate what an existing posting costs.</li>
 *   <li><strong>The requisition and the worker's status follow.</strong> Both
 *       are projections of this table, so both are refreshed here rather than
 *       left for a human to remember.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentService {

    private final DeploymentRepository deploymentRepository;
    private final ClientSiteRepository siteRepository;
    private final ManpowerCategoryRepository categoryRepository;
    private final WorkerService workerService;
    private final WorkerDocumentService workerDocumentService;
    private final RequisitionService requisitionService;
    private final RateCardService rateCardService;
    private final ClientAccessGuard accessGuard;
    private final DeploymentMapper mapper;
    private final AuditService auditService;

    // ==================================================================
    // Reading
    // ==================================================================

    @Transactional(readOnly = true)
    public PageResponse<DeploymentDtos.SummaryResponse> list(Long requestedClientId,
                                                             Long siteId,
                                                             Long workerId,
                                                             Long requisitionId,
                                                             DeploymentStatus status,
                                                             AuthenticatedUser caller,
                                                             Pageable pageable) {
        Long scopedClientId = accessGuard.resolveAccessibleClientId(requestedClientId, caller);

        Specification<Deployment> specification = Specification.<Deployment>unrestricted()
                .and(SpecificationUtils.notDeleted())
                .and(SpecificationUtils.equal("client.id", scopedClientId))
                .and(SpecificationUtils.equal("site.id", siteId))
                .and(SpecificationUtils.equal("worker.id", workerId))
                .and(SpecificationUtils.equal("requisition.id", requisitionId))
                .and(SpecificationUtils.equal("status", status));

        Page<Deployment> page = deploymentRepository.findAll(specification, pageable);
        return PageResponse.from(page, mapper::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public DeploymentDtos.Response get(Long deploymentId, AuthenticatedUser caller) {
        Deployment deployment = load(deploymentId);
        accessGuard.requireAccessTo(deployment.getClient().getId(), caller);
        return mapper.toResponse(deployment);
    }

    /** A worker's full posting history, newest first. */
    @Transactional(readOnly = true)
    public List<DeploymentDtos.Response> historyForWorker(Long workerId) {
        workerService.loadActive(workerId);
        return deploymentRepository.findHistoryForWorker(workerId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    /**
     * Everyone posted at a site on a date.
     *
     * <p>The basis of Phase 7's attendance sheet, and the list a client's own
     * portal shows - which is why it answers with the client-facing shape, with
     * no wage or margin on it.
     */
    @Transactional(readOnly = true)
    public List<DeploymentDtos.ClientResponse> listAtSiteOn(Long siteId,
                                                            LocalDate onDate,
                                                            AuthenticatedUser caller) {
        ClientSite site = loadSite(siteId);
        accessGuard.requireAccessTo(site.getClient().getId(), caller);

        return deploymentRepository.findActiveAtSiteOn(
                        siteId, onDate == null ? LocalDate.now() : onDate).stream()
                .map(mapper::toClientResponse)
                .toList();
    }

    // ==================================================================
    // Writing
    // ==================================================================

    /**
     * Posts a worker to a site.
     *
     * <p>The client comes from the site rather than the request, so there is no
     * way to post a worker to a site belonging to a different client - a
     * mistake that would bill the wrong company.
     */
    @Transactional
    public DeploymentDtos.Response create(DeploymentDtos.Request request,
                                          AuthenticatedUser actor,
                                          RequestContext context) {
        Worker worker = workerService.loadActive(request.workerId());
        ClientSite site = loadSite(request.siteId());

        if (!site.isActive()) {
            throw new BusinessRuleException(
                    ("Site %s is inactive, so workers cannot be posted to it.")
                            .formatted(site.getSiteCode()));
        }
        if (!site.getClient().allowsNewWork()) {
            throw new BusinessRuleException(
                    ("Client %s is %s, so new deployments cannot be created. Reactivate the "
                            + "client first.").formatted(
                                    site.getClient().displayName(),
                                    site.getClient().getStatus()));
        }

        Requisition requisition = resolveRequisition(request.requisitionId(), site, actor);
        ManpowerCategory category = resolveCategory(request.categoryId(), requisition);

        validateDates(request.startDate(), request.endDate());
        requireDeployable(worker, category, request.startDate());
        requireNoOverlap(worker, request.startDate(), request.endDate(), null);

        Deployment deployment = new Deployment();
        deployment.setWorker(worker);
        deployment.setClient(site.getClient());
        deployment.setSite(site);
        deployment.setRequisition(requisition);
        deployment.setCategory(category);
        deployment.setStartDate(request.startDate());
        deployment.setEndDate(request.endDate());
        deployment.setShift(request.shift() != null ? request.shift()
                : requisition == null ? null : requisition.getShift());
        deployment.setStatus(DeploymentStatus.ACTIVE);

        applyRates(deployment, request.monthlyWage(), request.billingRate(), actor);

        Deployment saved = deploymentRepository.save(deployment);

        // Both statuses are projections of this table, so they follow here.
        workerService.refreshDeploymentStatus(worker);
        requisitionService.refreshFulfilment(requisition, actor);

        auditService.recordEntityChange(AuditAction.CREATE, "Deployment", saved.getId(),
                actor.id(), actor.email(),
                "Posted %s (%s) to %s from %s at wage %s / billing %s".formatted(
                        worker.employeeCode(), worker.fullName(), site.getSiteCode(),
                        saved.getStartDate(), saved.getMonthlyWage(), saved.getBillingRate()),
                null, saved.displayLabel(), context);

        return mapper.toResponse(saved);
    }

    /**
     * Ends a posting.
     *
     * <p>Sets the end date as well as the status: a terminal status with an
     * open end date would still read as open-ended to the overlap check, and
     * would keep blocking the worker from being posted anywhere else.
     */
    @Transactional
    public DeploymentDtos.Response end(Long deploymentId,
                                       DeploymentDtos.EndRequest request,
                                       AuthenticatedUser actor,
                                       RequestContext context) {
        Deployment deployment = load(deploymentId);

        if (deployment.getStatus().isEnded()) {
            throw new BusinessRuleException(
                    ("This deployment already ended on %s (%s).").formatted(
                            deployment.getEndDate(), deployment.getStatus()));
        }
        if (request.status() == DeploymentStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "ACTIVE is the starting state, not an ending. Use RELEASED, COMPLETED or "
                            + "TRANSFERRED.");
        }
        if (request.endedOn().isBefore(deployment.getStartDate())) {
            throw new BusinessRuleException(
                    ("The end date is before the posting started on %s.")
                            .formatted(deployment.getStartDate()));
        }

        DeploymentStatus previous = deployment.getStatus();
        deployment.end(request.status(), request.endedOn(), request.reason());

        workerService.refreshDeploymentStatus(deployment.getWorker());
        requisitionService.refreshFulfilment(deployment.getRequisition(), actor);

        auditService.recordEntityChange(AuditAction.UPDATE, "Deployment", deploymentId,
                actor.id(), actor.email(),
                "Ended the posting of %s at %s on %s as %s: %s".formatted(
                        deployment.getWorker().employeeCode(),
                        deployment.getSite().getSiteCode(),
                        request.endedOn(), request.status(), request.reason()),
                previous.name(), request.status().name(), context);

        return mapper.toResponse(deployment);
    }

    /**
     * Moves a worker from one site to another in one step.
     *
     * <p>Exists because doing it as two calls is the easy way to get it wrong:
     * end on the 1st and start on the 1st and the worker is double-counted for
     * a day; end on the 1st and start on the 3rd and the client is short for
     * two. This ends the old posting the day before the new one starts, so
     * there is neither gap nor overlap, and both happen in one transaction.
     */
    @Transactional
    public DeploymentDtos.Response transfer(Long deploymentId,
                                            DeploymentDtos.TransferRequest request,
                                            AuthenticatedUser actor,
                                            RequestContext context) {
        Deployment current = load(deploymentId);

        if (current.getStatus().isEnded()) {
            throw new BusinessRuleException(
                    ("This deployment already ended on %s, so there is nothing to transfer. "
                            + "Create a new posting instead.").formatted(current.getEndDate()));
        }
        if (!request.effectiveFrom().isAfter(current.getStartDate())) {
            throw new BusinessRuleException(
                    ("The transfer date must be after the current posting started on %s.")
                            .formatted(current.getStartDate()));
        }

        ClientSite destination = loadSite(request.toSiteId());
        if (destination.getId().equals(current.getSite().getId())) {
            throw new BusinessRuleException(
                    "The worker is already posted at that site.");
        }
        if (!destination.isActive()) {
            throw new BusinessRuleException(
                    ("Site %s is inactive, so the worker cannot be transferred to it.")
                            .formatted(destination.getSiteCode()));
        }

        Worker worker = current.getWorker();
        Requisition requisition = resolveRequisition(
                request.requisitionId(), destination, actor);
        ManpowerCategory category = request.categoryId() != null
                ? loadCategory(request.categoryId())
                : current.getCategory();

        requireDeployable(worker, category, request.effectiveFrom());

        // Close the old posting first, so the overlap check below does not see
        // it as a conflict with its own successor.
        current.end(DeploymentStatus.TRANSFERRED, request.effectiveFrom().minusDays(1),
                request.reason());

        requireNoOverlap(worker, request.effectiveFrom(), null, deploymentId);

        Deployment moved = new Deployment();
        moved.setWorker(worker);
        moved.setClient(destination.getClient());
        moved.setSite(destination);
        moved.setRequisition(requisition);
        moved.setCategory(category);
        moved.setStartDate(request.effectiveFrom());
        moved.setShift(request.shift() != null ? request.shift() : current.getShift());
        moved.setStatus(DeploymentStatus.ACTIVE);
        moved.setNotes("Transferred from " + current.displayLabel());

        // Re-resolved rather than copied: the destination may be a different
        // client, in a different state, with a different rate card.
        applyRates(moved, null, null, actor);

        Deployment saved = deploymentRepository.save(moved);

        workerService.refreshDeploymentStatus(worker);
        requisitionService.refreshFulfilment(current.getRequisition(), actor);
        requisitionService.refreshFulfilment(requisition, actor);

        auditService.recordEntityChange(AuditAction.UPDATE, "Deployment", saved.getId(),
                actor.id(), actor.email(),
                "Transferred %s from %s to %s effective %s: %s".formatted(
                        worker.employeeCode(), current.getSite().getSiteCode(),
                        destination.getSiteCode(), request.effectiveFrom(), request.reason()),
                current.displayLabel(), saved.displayLabel(), context);

        return mapper.toResponse(saved);
    }

    // ==================================================================
    // Invariants
    // ==================================================================

    /**
     * Refuses a posting that would overlap one the worker already holds.
     *
     * <p>The comparison runs in {@code DateUtils.overlaps} rather than SQL,
     * because a null end date means open-ended and expressing that in JPQL is
     * easy to get subtly wrong. The set of one worker's active postings is
     * tiny, so there is nothing to gain by pushing it down.
     *
     * <p>The message names the conflict. "Worker unavailable" sends somebody to
     * the database; "already at Bharat Textiles until 31 March" does not.
     */
    private void requireNoOverlap(Worker worker, LocalDate from, LocalDate to,
                                  Long excludeDeploymentId) {
        Deployment candidate = new Deployment();
        candidate.setStartDate(from);
        candidate.setEndDate(to);

        for (Deployment existing : deploymentRepository.findActiveForWorker(worker.getId())) {
            if (existing.getId().equals(excludeDeploymentId)) {
                continue;
            }
            if (candidate.overlaps(existing)) {
                throw new BusinessRuleException(
                        ("%s is already posted to %s from %s %s. A worker cannot be at two "
                                + "sites at once - end that posting first, or use transfer.")
                                .formatted(
                                        worker.fullName(),
                                        existing.displayLabel(),
                                        existing.getStartDate(),
                                        existing.getEndDate() == null
                                                ? "with no end date"
                                                : "until " + existing.getEndDate()));
            }
        }
    }

    /**
     * Refuses to post a worker who should not be posted.
     *
     * <p>The police verification gate is the one with statutory force: under
     * the Private Security Agencies (Regulation) Act a security guard may not
     * be deployed without a clear verification, and the principal employer is
     * liable too. Refusing here is cheaper than a licence problem later.
     */
    private void requireDeployable(Worker worker, ManpowerCategory category, LocalDate startDate) {
        if (worker.getStatus().hasExited()) {
            throw new BusinessRuleException(
                    ("%s left on %s, so they cannot be posted.").formatted(
                            worker.fullName(), worker.getLastWorkingDay()));
        }
        if (!worker.getStatus().isDeployable()) {
            throw new BusinessRuleException(
                    ("%s is %s, so they cannot be posted. An attendance sheet for someone on "
                            + "approved leave is nobody's to fill in.")
                            .formatted(worker.fullName(), worker.getStatus()));
        }

        if (!requiresPoliceVerification(category)) {
            return;
        }

        if (!worker.getPoliceVerificationStatus().permitsSecurityDeployment()) {
            throw new BusinessRuleException(
                    ("%s cannot be posted as %s: their police verification is %s. Under the "
                            + "Private Security Agencies (Regulation) Act a guard may not be "
                            + "deployed without a clear verification, and the client is liable "
                            + "for checking it too.").formatted(
                                    worker.fullName(), category.getName(),
                                    worker.getPoliceVerificationStatus()));
        }

        // Status says cleared, but the certificate itself has to be on file and
        // unexpired - the status is somebody's data entry, the document is the
        // evidence an inspector asks for.
        boolean hasEvidence = workerDocumentService.latestValidEvidence(
                worker.getId(), WorkerDocumentType.POLICE_VERIFICATION, startDate).isPresent();
        if (!hasEvidence) {
            log.warn("Worker {} is marked {} for police verification but has no verified, "
                            + "unexpired certificate on file. Posting them as {} anyway; this "
                            + "is a compliance gap if an inspection asks for the document.",
                    worker.employeeCode(), worker.getPoliceVerificationStatus(),
                    category.getName());
        }
    }

    /**
     * Whether a role is a security role.
     *
     * <p>Keyed off the category code rather than a column, because the codes are
     * seeded and stable, and adding a boolean to {@code manpower_categories}
     * would need every existing row reviewed. Revisit if the list grows.
     */
    private boolean requiresPoliceVerification(ManpowerCategory category) {
        String code = category.getCode();
        return code != null && (code.contains("SECURITY") || code.contains("GUARD")
                || code.contains("BOUNCER") || code.contains("GUNMAN"));
    }

    /**
     * Snapshots the wage and billing rate onto the posting.
     *
     * <p>Resolved from the rate card applicable on the start date unless the
     * caller overrode them. Copied rather than referenced so a later rate
     * revision cannot restate what this posting costs - Phase 9 invoices from
     * these numbers, and an invoice that changes after it was issued is a
     * dispute.
     */
    private void applyRates(Deployment deployment, BigDecimal wageOverride,
                            BigDecimal billingOverride, AuthenticatedUser caller) {
        if (wageOverride != null && billingOverride != null) {
            deployment.setMonthlyWage(wageOverride);
            deployment.setBillingRate(billingOverride);
            log.info("Deployment of worker {} uses overridden rates (wage {}, billing {}) "
                            + "rather than the rate card.",
                    deployment.getWorker().employeeCode(), wageOverride, billingOverride);
            return;
        }

        // Goes through the same resolver payroll and billing use, so a
        // deployment is costed exactly as it will later be paid and invoiced -
        // and so the ambiguity warning fires here too, where somebody is
        // actually looking.
        RateCardDtos.ResolvedResponse resolved = rateCardService.resolve(
                deployment.getClient().getId(),
                deployment.getCategory().getId(),
                deployment.getSite().getId(),
                deployment.getStartDate(),
                caller);

        if (resolved.rateCard() == null) {
            throw new BusinessRuleException(
                    ("No rate card covers %s at %s on %s, so there is no wage or billing rate "
                            + "to apply. Add a rate card for that category, or supply both "
                            + "monthlyWage and billingRate explicitly.").formatted(
                                    deployment.getCategory().getName(),
                                    deployment.getSite().getSiteCode(),
                                    deployment.getStartDate()));
        }

        if (resolved.ambiguous()) {
            // Already logged by the resolver, but a deployment is a commitment
            // to a client, so it is worth saying again with the worker named.
            log.warn("Rate for the deployment of worker {} at {} was resolved ambiguously: {}. "
                            + "Fix the overlapping rate cards.",
                    deployment.getWorker().employeeCode(), deployment.getSite().getSiteCode(),
                    resolved.resolution());
        }

        deployment.setMonthlyWage(wageOverride != null
                ? wageOverride : resolved.rateCard().monthlyWage());
        deployment.setBillingRate(billingOverride != null
                ? billingOverride : resolved.rateCard().billingRate());

        if (deployment.isBillingBelowWage()) {
            log.warn("Deployment of worker {} at {} bills {} against a wage of {} - the company "
                            + "loses money on this posting.",
                    deployment.getWorker().employeeCode(), deployment.getSite().getSiteCode(),
                    deployment.getBillingRate(), deployment.getMonthlyWage());
        }
    }

    // ==================================================================
    // Internals
    // ==================================================================

    private Deployment load(Long deploymentId) {
        return deploymentRepository.findActiveById(deploymentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Deployment", deploymentId));
    }

    private ClientSite loadSite(Long siteId) {
        return siteRepository.findActiveById(siteId)
                .orElseThrow(() -> ResourceNotFoundException.of("Site", siteId));
    }

    private ManpowerCategory loadCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> ResourceNotFoundException.of("Manpower category", categoryId));
    }

    /** Resolves the optional requisition, checking it is for this very site. */
    private Requisition resolveRequisition(Long requisitionId, ClientSite site,
                                           AuthenticatedUser caller) {
        if (requisitionId == null) {
            return null;
        }
        Requisition requisition = requisitionService.loadAccessible(requisitionId, caller);

        if (!requisition.getSite().getId().equals(site.getId())) {
            throw new BusinessRuleException(
                    ("Requisition %s is for site %s, not %s. Fulfilment is counted per "
                            + "requisition, so posting against the wrong one would make both "
                            + "counts wrong.").formatted(
                                    requisition.requisitionNumber(),
                                    requisition.getSite().getSiteCode(),
                                    site.getSiteCode()));
        }
        if (!requisition.getStatus().acceptsDeployments()) {
            throw new BusinessRuleException(
                    ("Requisition %s is %s, so workers cannot be posted against it. It has to "
                            + "be approved first.").formatted(
                                    requisition.requisitionNumber(), requisition.getStatus()));
        }
        return requisition;
    }

    private ManpowerCategory resolveCategory(Long categoryId, Requisition requisition) {
        if (categoryId != null) {
            return loadCategory(categoryId);
        }
        if (requisition != null) {
            return requisition.getCategory();
        }
        throw new BusinessRuleException(
                "A category is required when there is no requisition to take it from.");
    }

    private void validateDates(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new BusinessRuleException(
                    "The end date is before the start date. Swap them, or leave the end blank "
                            + "for an open-ended posting.");
        }
    }
}
