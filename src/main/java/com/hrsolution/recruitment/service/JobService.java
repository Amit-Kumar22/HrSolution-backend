package com.hrsolution.recruitment.service;

import com.hrsolution.audit.entity.AuditAction;
import com.hrsolution.audit.service.AuditService;
import com.hrsolution.catalog.entity.ManpowerCategory;
import com.hrsolution.catalog.repository.ManpowerCategoryRepository;
import com.hrsolution.client.entity.Client;
import com.hrsolution.client.entity.ClientSite;
import com.hrsolution.client.repository.ClientRepository;
import com.hrsolution.client.repository.ClientSiteRepository;
import com.hrsolution.common.error.BusinessRuleException;
import com.hrsolution.common.error.DuplicateResourceException;
import com.hrsolution.common.error.ResourceNotFoundException;
import com.hrsolution.common.repository.SpecificationUtils;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.recruitment.dto.JobDtos;
import com.hrsolution.recruitment.entity.ApplicationStatus;
import com.hrsolution.recruitment.entity.Job;
import com.hrsolution.recruitment.entity.JobStatus;
import com.hrsolution.recruitment.mapper.RecruitmentMapper;
import com.hrsolution.recruitment.repository.ApplicationRepository;
import com.hrsolution.recruitment.repository.JobRepository;
import com.hrsolution.requisition.entity.Requisition;
import com.hrsolution.requisition.repository.RequisitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Job adverts.
 *
 * <p>Two rules here are worth knowing before changing anything:
 *
 * <ul>
 *   <li><strong>The public read path never loads the client.</strong> A job
 *       records which client it is for, and that is confidential - see
 *       {@link Job} for why. The public queries fetch only the category, so
 *       there is nothing in hand to leak even if a mapping were added
 *       carelessly.</li>
 *   <li><strong>Openings are counted, not decremented.</strong> There is no
 *       stored "remaining" column, because it would drift the moment an
 *       application was rejected after being selected. The count is derived
 *       from the applications table on read.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    /** Statuses that represent a commitment against an opening. */
    private static final Set<ApplicationStatus> COMMITTED = EnumSet.of(
            ApplicationStatus.SELECTED, ApplicationStatus.OFFERED, ApplicationStatus.JOINED);

    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final ManpowerCategoryRepository categoryRepository;
    private final ClientRepository clientRepository;
    private final ClientSiteRepository siteRepository;
    private final RequisitionRepository requisitionRepository;
    private final RecruitmentMapper mapper;
    private final AuditService auditService;

    // ==================================================================
    // Public careers site
    // ==================================================================

    @Transactional(readOnly = true)
    public PageResponse<JobDtos.PublicSummaryResponse> listPublic(Long categoryId,
                                                                  String city,
                                                                  Pageable pageable) {
        Page<Job> page = jobRepository.findPubliclyVisible(
                LocalDate.now(), categoryId, blankToNull(city), pageable);
        return PageResponse.from(page, mapper::toPublicSummaryResponse);
    }

    @Transactional(readOnly = true)
    public JobDtos.PublicResponse getPublicBySlug(String slug) {
        // A lapsed or unpublished advert is a 404 at the public URL rather than
        // a 200 the caller has to interpret. The expiry check is in the query.
        return jobRepository.findPubliclyVisibleBySlug(slug, LocalDate.now())
                .map(mapper::toPublicResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No open vacancy exists at that address. It may have been filled or "
                                + "withdrawn."));
    }

    // ==================================================================
    // Staff
    // ==================================================================

    @Transactional(readOnly = true)
    public PageResponse<JobDtos.SummaryResponse> list(String search,
                                                      JobStatus status,
                                                      Long categoryId,
                                                      Long clientId,
                                                      Pageable pageable) {
        Specification<Job> specification = Specification.<Job>unrestricted()
                .and(SpecificationUtils.notDeleted())
                .and(SpecificationUtils.equal("status", status))
                .and(SpecificationUtils.equal("category.id", categoryId))
                .and(SpecificationUtils.equal("client.id", clientId))
                .and(SpecificationUtils.anyContainsIgnoreCase(search, "title", "slug", "city"));

        Page<Job> page = jobRepository.findAll(specification, pageable);

        // One grouped query for the whole page rather than a count per row.
        Map<Long, Long> totals = totalApplicationsByJob(
                page.getContent().stream().map(Job::getId).toList());

        return PageResponse.from(page, job -> {
            JobDtos.SummaryResponse summary = mapper.toSummaryResponse(job);
            return new JobDtos.SummaryResponse(
                    summary.id(), summary.slug(), summary.title(), summary.categoryName(),
                    summary.city(), summary.openings(),
                    totals.getOrDefault(job.getId(), 0L),
                    summary.status(), summary.expiresOn(), summary.expired(),
                    summary.publiclyVisible());
        });
    }

    private Map<Long, Long> totalApplicationsByJob(List<Long> jobIds) {
        if (jobIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> totals = new HashMap<>();
        for (ApplicationRepository.JobStatusCount row
                : applicationRepository.countByStatusForJobs(jobIds)) {
            totals.merge(row.getJobId(), row.getTotal(), Long::sum);
        }
        return totals;
    }

    @Transactional(readOnly = true)
    public JobDtos.Response get(Long jobId) {
        return withCounts(load(jobId));
    }

    @Transactional
    public JobDtos.Response create(JobDtos.Request request,
                                   AuthenticatedUser actor,
                                   RequestContext context) {
        String slug = normaliseSlug(request.slug());
        if (jobRepository.existsBySlugIgnoringSoftDelete(slug)) {
            throw DuplicateResourceException.of("A job", "slug", slug);
        }

        validateRanges(request);

        Job job = new Job();
        mapper.applyRequest(request, job);
        job.setSlug(slug);
        job.setCategory(loadCategory(request.categoryId()));
        applyClientAndSite(job, request.clientId(), request.siteId());
        applyRequisition(job, request.requisitionId());
        job.setOpenings(request.openings() == null ? 1 : request.openings());
        job.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        // Always starts as a draft. Publishing is a separate, audited decision -
        // a job created straight into OPEN would be advertised before anyone
        // had read it back.
        job.setStatus(JobStatus.DRAFT);

        Job saved = jobRepository.save(job);

        auditService.recordEntityChange(AuditAction.CREATE, "Job", saved.getId(),
                actor.id(), actor.email(),
                "Created job advert '%s' (%s), %d opening(s)".formatted(
                        saved.getTitle(), saved.getSlug(), saved.getOpenings()),
                null, saved.getSlug(), context);

        return withCounts(saved);
    }

    @Transactional
    public JobDtos.Response update(Long jobId,
                                   JobDtos.Request request,
                                   AuthenticatedUser actor,
                                   RequestContext context) {
        Job job = load(jobId);
        String newSlug = normaliseSlug(request.slug());

        if (!job.getSlug().equals(newSlug)) {
            if (jobRepository.existsBySlugExcluding(newSlug, jobId)) {
                throw DuplicateResourceException.of("A job", "slug", newSlug);
            }
            if (job.getStatus().isPubliclyVisible()) {
                // Not refused: a typo in a live slug is worth fixing. But it
                // breaks every inbound link and any shared advert, so it is
                // called out rather than folded into the generic field diff.
                log.warn("Slug of live job {} changed '{}' -> '{}'. Inbound links to the old "
                        + "address will 404.", jobId, job.getSlug(), newSlug);
            }
        }

        validateRanges(request);

        int previousOpenings = job.getOpenings();

        mapper.applyRequest(request, job);
        job.setSlug(newSlug);
        job.setCategory(loadCategory(request.categoryId()));
        applyClientAndSite(job, request.clientId(), request.siteId());
        applyRequisition(job, request.requisitionId());
        if (request.openings() != null) {
            job.setOpenings(request.openings());
        }
        if (request.displayOrder() != null) {
            job.setDisplayOrder(request.displayOrder());
        }

        long committed = committedCount(jobId);
        if (job.getOpenings() < committed) {
            // Allowed, because a client really can cut a requirement after
            // offers went out, but the recruiter has to see it.
            log.warn("Job {} reduced to {} opening(s) while {} application(s) are already at "
                            + "SELECTED or beyond. The job is over-committed.",
                    jobId, job.getOpenings(), committed);
        }

        auditService.recordEntityChange(AuditAction.UPDATE, "Job", jobId,
                actor.id(), actor.email(),
                "Updated job advert '%s'".formatted(job.getTitle()),
                String.valueOf(previousOpenings), String.valueOf(job.getOpenings()), context);

        return withCounts(job);
    }

    /**
     * Moves a job through its lifecycle.
     *
     * <p>Unlike applications, job statuses are not a constrained graph - a
     * recruiter may reasonably reopen a closed advert or put a draft straight
     * on hold. What is checked is the one transition with a consequence the
     * caller may not intend: publishing an advert that is already lapsed, which
     * would put a vacancy on the site that nobody can apply to.
     */
    @Transactional
    public JobDtos.Response changeStatus(Long jobId,
                                         JobDtos.StatusChangeRequest request,
                                         AuthenticatedUser actor,
                                         RequestContext context) {
        Job job = load(jobId);
        JobStatus previous = job.getStatus();
        JobStatus target = request.status();

        if (target == JobStatus.OPEN && job.hasExpiredOn(LocalDate.now())) {
            throw new BusinessRuleException(
                    ("This advert's closing date was %s, so publishing it would show a vacancy "
                            + "nobody can apply to. Change or clear the closing date first.")
                            .formatted(job.getExpiresOn()));
        }

        if (target == JobStatus.OPEN && job.getCategory() == null) {
            throw new BusinessRuleException(
                    "A job must have a manpower category before it can be advertised.");
        }

        if (previous == target) {
            return withCounts(job);
        }

        job.setStatus(target);

        if (target == JobStatus.FILLED) {
            long committed = committedCount(jobId);
            if (committed < job.getOpenings()) {
                log.info("Job {} marked FILLED with {} of {} opening(s) committed.",
                        jobId, committed, job.getOpenings());
            }
        }

        auditService.recordEntityChange(AuditAction.UPDATE, "Job", jobId,
                actor.id(), actor.email(),
                "Job '%s' moved %s -> %s%s".formatted(job.getTitle(), previous, target,
                        request.reason() == null ? "" : ": " + request.reason()),
                previous.name(), target.name(), context);

        return withCounts(job);
    }

    /**
     * Soft-deletes a job.
     *
     * <p>Refused once applications exist. Hiding the advert is what CLOSED is
     * for; removing it would orphan a candidate's application history and, from
     * Phase 6, the record of why a worker was hired.
     */
    @Transactional
    public void delete(Long jobId, AuthenticatedUser actor, RequestContext context) {
        Job job = load(jobId);

        long applications = applicationRepository.countByJobAndStatusIn(
                jobId, EnumSet.allOf(ApplicationStatus.class));
        if (applications > 0) {
            throw new BusinessRuleException(
                    ("This job has %d application(s), so deleting it would orphan their "
                            + "history. Set its status to CLOSED instead.").formatted(applications));
        }

        job.markDeleted(actor.email());
        job.setStatus(JobStatus.CLOSED);

        auditService.recordEntityChange(AuditAction.DELETE, "Job", jobId,
                actor.id(), actor.email(),
                "Deleted job advert '%s'".formatted(job.getTitle()), null, null, context);
    }

    // ==================================================================
    // Internals
    // ==================================================================

    /** Loads an active job, for other services in this module. */
    @Transactional(readOnly = true)
    public Job loadForApplication(Long jobId) {
        return load(jobId);
    }

    private Job load(Long jobId) {
        return jobRepository.findActiveById(jobId)
                .orElseThrow(() -> ResourceNotFoundException.of("Job", jobId));
    }

    private ManpowerCategory loadCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> ResourceNotFoundException.of("Manpower category", categoryId));
    }

    /**
     * Resolves the optional client and site, checking that the site belongs to
     * the client when both are given.
     *
     * <p>A site from a different client would silently advertise one client's
     * vacancy at another's premises, and the mistake is invisible on the public
     * advert because neither is published.
     */
    private void applyClientAndSite(Job job, Long clientId, Long siteId) {
        Client client = null;
        if (clientId != null) {
            client = clientRepository.findActiveById(clientId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Client", clientId));
        }
        job.setClient(client);

        if (siteId == null) {
            job.setSite(null);
            return;
        }

        ClientSite site = siteRepository.findActiveById(siteId)
                .orElseThrow(() -> ResourceNotFoundException.of("Site", siteId));

        if (client == null) {
            throw new BusinessRuleException(
                    "A site was given without a client. Set clientId as well, or clear siteId.");
        }
        if (!site.getClient().getId().equals(client.getId())) {
            throw new BusinessRuleException(
                    ("Site %s belongs to a different client, so it cannot be used for this "
                            + "job.").formatted(site.getSiteCode()));
        }
        job.setSite(site);
    }

    /**
     * Resolves the optional requisition, checking it belongs to the same client.
     *
     * <p>A job sourcing for another client's requirement would route its
     * applicants to the wrong place, and because neither the client nor the
     * requisition appears on the public advert, nothing about the mistake is
     * visible from outside.
     */
    private void applyRequisition(Job job, Long requisitionId) {
        if (requisitionId == null) {
            job.setRequisition(null);
            return;
        }

        Requisition requisition = requisitionRepository.findActiveById(requisitionId)
                .orElseThrow(() -> ResourceNotFoundException.of("Requisition", requisitionId));

        if (job.getClient() == null) {
            // Inherit rather than refuse: the requisition already names a
            // client, so asking the caller to repeat it is pointless ceremony.
            job.setClient(requisition.getClient());
        } else if (!job.getClient().getId().equals(requisition.getClient().getId())) {
            throw new BusinessRuleException(
                    ("Requisition %s belongs to a different client, so this job cannot source "
                            + "for it.").formatted(requisition.requisitionNumber()));
        }

        if (requisition.getStatus().isTerminal()) {
            throw new BusinessRuleException(
                    ("Requisition %s is %s, so there is nothing left to recruit for.")
                            .formatted(requisition.requisitionNumber(),
                                    requisition.getStatus()));
        }

        job.setRequisition(requisition);
    }

    private void validateRanges(JobDtos.Request request) {
        if (request.salaryMin() != null && request.salaryMax() != null
                && request.salaryMin().compareTo(request.salaryMax()) > 0) {
            throw new BusinessRuleException(
                    "The minimum salary is above the maximum. Swap them, or leave one blank.");
        }
        if (request.experienceMinMonths() != null && request.experienceMaxMonths() != null
                && request.experienceMinMonths() > request.experienceMaxMonths()) {
            throw new BusinessRuleException(
                    "The minimum experience is above the maximum. Swap them, or leave one blank.");
        }
        if (request.expiresOn() != null && request.expiresOn().isBefore(LocalDate.now())) {
            throw new BusinessRuleException(
                    "The closing date is in the past, so the advert would never be visible.");
        }
    }

    /** Fills in the three counts the mapper deliberately leaves alone. */
    private JobDtos.Response withCounts(Job job) {
        JobDtos.Response base = mapper.toResponse(job);
        long committed = committedCount(job.getId());
        long total = applicationRepository.countByJobAndStatusIn(
                job.getId(), EnumSet.allOf(ApplicationStatus.class));

        return new JobDtos.Response(
                base.id(), base.slug(), base.title(), base.summary(), base.description(),
                base.categoryId(), base.categoryName(), base.skillLevel(),
                base.clientId(), base.clientName(), base.siteId(), base.siteName(),
                base.requisitionId(), base.requisitionNumber(),
                base.location(), base.city(), base.state(),
                base.openings(), committed, job.getOpenings() - committed, total,
                base.salaryMin(), base.salaryMax(), base.salaryRangeLabel(),
                base.experienceMinMonths(), base.experienceMaxMonths(), base.experienceLabel(),
                base.shift(), base.status(),
                base.expiresOn(), base.expired(), base.daysUntilExpiry(), base.publiclyVisible(),
                base.metaTitle(), base.metaDescription(), base.displayOrder());
    }

    private long committedCount(Long jobId) {
        return applicationRepository.countByJobAndStatusIn(jobId, COMMITTED);
    }

    /** The statuses that consume an opening, for other services in this module. */
    public static List<ApplicationStatus> committedStatuses() {
        return List.copyOf(COMMITTED);
    }

    private static String normaliseSlug(String slug) {
        return slug == null ? null : slug.trim().toLowerCase();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
