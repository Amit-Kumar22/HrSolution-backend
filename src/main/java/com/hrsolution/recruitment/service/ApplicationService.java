package com.hrsolution.recruitment.service;

import com.hrsolution.audit.entity.AuditAction;
import com.hrsolution.audit.service.AuditService;
import com.hrsolution.common.error.BusinessRuleException;
import com.hrsolution.common.error.DuplicateResourceException;
import com.hrsolution.common.error.ResourceNotFoundException;
import com.hrsolution.common.repository.SpecificationUtils;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.document.entity.StoredDocument;
import com.hrsolution.enquiry.service.SpamGuard;
import com.hrsolution.recruitment.ApplicationReference;
import com.hrsolution.recruitment.dto.ApplicationDtos;
import com.hrsolution.recruitment.dto.InterviewDtos;
import com.hrsolution.recruitment.entity.Application;
import com.hrsolution.recruitment.entity.ApplicationStatus;
import com.hrsolution.recruitment.entity.Candidate;
import com.hrsolution.recruitment.entity.CandidateSource;
import com.hrsolution.recruitment.entity.Interview;
import com.hrsolution.recruitment.entity.Job;
import com.hrsolution.recruitment.mapper.RecruitmentMapper;
import com.hrsolution.recruitment.repository.ApplicationRepository;
import com.hrsolution.recruitment.repository.InterviewRepository;
import com.hrsolution.recruitment.repository.JobRepository;
import com.hrsolution.user.entity.User;
import com.hrsolution.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applications - the hiring funnel.
 *
 * <h2>Transitions are the entity's job, not this service's</h2>
 *
 * <p>Every status change goes through {@link Application#moveTo}, which
 * validates against {@link ApplicationStatus#allowedNextStatuses()} and appends
 * to the trail in the same call. This service translates the entity's
 * {@link IllegalStateException} into a {@link BusinessRuleException} and adds
 * the context a user needs, but it does not decide what is legal. Four callers
 * drive this funnel and a rule enforced here would be a rule the other three
 * could bypass.
 *
 * <h2>Why the duplicate check is a query, not a caught constraint</h2>
 *
 * <p>{@code (job_id, candidate_id)} is unique in the database, so a double
 * submission is impossible either way. It is checked up front so the caller
 * gets a {@code 409} with a readable message instead of a constraint violation
 * surfacing as a 500 - and so an anonymous applicant is told they already
 * applied without the response confirming whose email it is.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final InterviewRepository interviewRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final CandidateService candidateService;
    private final RecruitmentMapper mapper;
    private final AuditService auditService;
    private final SpamGuard spamGuard;

    // ==================================================================
    // Applying
    // ==================================================================

    /**
     * The anonymous apply path.
     *
     * <p>A tripped honeypot or an implausibly fast submission flags the
     * candidate and stores the application anyway, exactly as the enquiry form
     * does. Rejecting outright would occasionally discard a real applicant, and
     * a human reviewing a flagged row loses nothing.
     */
    @Transactional
    public ApplicationDtos.ApplyResponse applyPublicly(String slug,
                                                       ApplicationDtos.PublicApplyRequest request,
                                                       MultipartFile resume,
                                                       RequestContext context) {
        Job job = loadOpenJobBySlug(slug);

        SpamGuard.Verdict verdict = spamGuard.assess(
                request.website(), request.formRenderedAt(), context,
                request.fullName(), request.coverNote());

        Candidate candidate = candidateService.resolveForApplication(
                request.email(), request.fullName(), request.phone(),
                request.city(), request.state(),
                request.totalExperienceMonths(), request.highestQualification(),
                CandidateSource.WEBSITE);

        if (verdict.suspected()) {
            candidate.flagAsSpam(verdict.reason());
        }

        requireNotAlreadyApplied(job, candidate, true);

        Application application = new Application();
        application.setJob(job);
        application.setCandidate(candidate);
        application.setAppliedOn(LocalDate.now());
        application.setSource(CandidateSource.WEBSITE);
        application.setCoverNote(request.coverNote());
        application.setExpectedSalary(request.expectedSalary());
        application.setStatus(ApplicationStatus.APPLIED);

        Application saved = applicationRepository.save(application);

        // Needs the application id as the document owner, so the upload comes
        // after the insert. A failure here rolls the whole thing back, which is
        // right: an application whose CV silently vanished is worse than one
        // the applicant can resubmit.
        attachResumeIfPresent(saved, candidate, resume);

        // Null actor: the candidate acted, not a member of staff. That
        // distinction is what makes the trail auditable.
        saved.recordSubmission(null, candidate.getEmail(),
                verdict.suspected()
                        ? "Submitted via the public form. Flagged: " + verdict.reason()
                        : "Submitted via the public form");

        auditService.recordEntityChange(AuditAction.CREATE, "Application", saved.getId(),
                null, candidate.getEmail(),
                "Public application to '%s' by %s%s".formatted(
                        job.getTitle(), candidate.getEmail(),
                        verdict.suspected() ? " (flagged as possible spam)" : ""),
                null, ApplicationReference.of(saved.getId()), context);

        return acknowledgement(saved, job);
    }

    /** The signed-in candidate's apply path. */
    @Transactional
    public ApplicationDtos.ApplyResponse applyAsCandidate(Long jobId,
                                                          ApplicationDtos.SelfApplyRequest request,
                                                          AuthenticatedUser caller,
                                                          RequestContext context) {
        Job job = loadOpenJob(jobId);
        Candidate candidate = candidateService.resolveForSelfService(caller);

        requireNotAlreadyApplied(job, candidate, false);

        Application application = new Application();
        application.setJob(job);
        application.setCandidate(candidate);
        application.setAppliedOn(LocalDate.now());
        application.setSource(CandidateSource.WEBSITE);
        application.setCoverNote(request.coverNote());
        application.setExpectedSalary(request.expectedSalary());
        application.setStatus(ApplicationStatus.APPLIED);
        // Snapshots the CV on file at the moment of applying, so a later
        // profile update does not rewrite what the recruiter was sent.
        application.setResumeDocument(candidate.getResumeDocument());

        Application saved = applicationRepository.save(application);
        saved.recordSubmission(null, candidate.getEmail(), "Applied from the candidate portal");

        auditService.recordEntityChange(AuditAction.CREATE, "Application", saved.getId(),
                caller.id(), caller.email(),
                "Candidate applied to '%s'".formatted(job.getTitle()),
                null, ApplicationReference.of(saved.getId()), context);

        return acknowledgement(saved, job);
    }

    /**
     * Staff recording an application on someone's behalf - a walk-in, a phone
     * call, a CV forwarded by an agency.
     */
    @Transactional
    public ApplicationDtos.Response createForCandidate(Long jobId,
                                                       ApplicationDtos.StaffRequest request,
                                                       AuthenticatedUser actor,
                                                       RequestContext context) {
        Job job = loadJob(jobId);
        Candidate candidate = candidateService.loadActive(request.candidateId());

        // Staff may record an application against a job that is not currently
        // advertised - a walk-in for a role about to reopen is normal - but not
        // against one that is finished, which would be a data-entry mistake.
        if (job.getStatus().isFinished()) {
            throw new BusinessRuleException(
                    ("Job '%s' is %s, so no further applications can be recorded against it.")
                            .formatted(job.getTitle(), job.getStatus()));
        }

        requireNotAlreadyApplied(job, candidate, false);

        Application application = new Application();
        application.setJob(job);
        application.setCandidate(candidate);
        application.setAppliedOn(LocalDate.now());
        application.setSource(request.source() == null ? CandidateSource.WALK_IN : request.source());
        application.setCoverNote(request.coverNote());
        application.setExpectedSalary(request.expectedSalary());
        application.setStatus(ApplicationStatus.APPLIED);
        application.setResumeDocument(candidate.getResumeDocument());
        application.setAssignedTo(resolveAssignee(request.assignedToUserId()));

        Application saved = applicationRepository.save(application);
        saved.recordSubmission(actor.id(), actor.email(),
                request.note() == null
                        ? "Recorded by staff on the candidate's behalf"
                        : request.note());

        auditService.recordEntityChange(AuditAction.CREATE, "Application", saved.getId(),
                actor.id(), actor.email(),
                "Recorded application of %s to '%s'".formatted(
                        candidate.getFullName(), job.getTitle()),
                null, ApplicationReference.of(saved.getId()), context);

        return withInterviewCounts(saved);
    }

    // ==================================================================
    // Moving through the funnel
    // ==================================================================

    @Transactional
    public ApplicationDtos.Response changeStatus(Long applicationId,
                                                 ApplicationDtos.StatusChangeRequest request,
                                                 AuthenticatedUser actor,
                                                 RequestContext context) {
        Application application = load(applicationId);
        ApplicationStatus previous = application.getStatus();
        ApplicationStatus target = request.status();

        // A rejection with no reason is useless to the next recruiter who opens
        // the record, and to the candidate if they ask.
        if (target == ApplicationStatus.REJECTED
                && (request.note() == null || request.note().isBlank())) {
            throw new BusinessRuleException(
                    "A reason is required when rejecting an application. It is the only thing "
                            + "the next recruiter to open this record will have to go on.");
        }

        if (target == ApplicationStatus.INTERVIEW_SCHEDULED
                && !interviewRepository.hasUpcomingInterview(applicationId, Instant.now())) {
            throw new BusinessRuleException(
                    "There is no upcoming interview on this application, so it cannot be moved "
                            + "to INTERVIEW_SCHEDULED. Schedule the interview first - that moves "
                            + "it for you.");
        }

        warnIfOverCommitting(application, target);

        moveOrExplain(application, target, actor.id(), actor.email(), request.note());

        if (target == ApplicationStatus.REJECTED) {
            application.setRejectionReason(request.note());
        }

        if (target == ApplicationStatus.JOINED) {
            // Phase 6 turns this into a worker record via workers.candidate_id.
            // Logged rather than acted on because the table does not exist yet.
            log.info("Application {} reached JOINED for candidate {} on job '{}'. "
                            + "Phase 6 converts this into a worker record.",
                    applicationId, application.getCandidate().getId(),
                    application.getJob().getTitle());
        }

        auditService.recordEntityChange(AuditAction.UPDATE, "Application", applicationId,
                actor.id(), actor.email(),
                "%s moved %s -> %s".formatted(
                        ApplicationReference.of(applicationId), previous, target),
                previous.name(), target.name(), context);

        return withInterviewCounts(application);
    }

    @Transactional
    public ApplicationDtos.Response assign(Long applicationId,
                                           ApplicationDtos.AssignRequest request,
                                           AuthenticatedUser actor,
                                           RequestContext context) {
        Application application = load(applicationId);
        User previous = application.getAssignedTo();
        User assignee = resolveAssignee(request.assignedToUserId());

        application.setAssignedTo(assignee);

        auditService.recordEntityChange(AuditAction.UPDATE, "Application", applicationId,
                actor.id(), actor.email(),
                assignee == null
                        ? "Unassigned " + ApplicationReference.of(applicationId)
                        : "Assigned %s to %s".formatted(
                                ApplicationReference.of(applicationId), assignee.getEmail()),
                previous == null ? null : previous.getEmail(),
                assignee == null ? null : assignee.getEmail(), context);

        return withInterviewCounts(application);
    }

    /** The candidate withdrawing their own application. */
    @Transactional
    public ApplicationDtos.SelfResponse withdrawOwn(Long applicationId,
                                                    ApplicationDtos.WithdrawRequest request,
                                                    AuthenticatedUser caller,
                                                    RequestContext context) {
        Application application = loadOwn(applicationId, caller);

        if (application.getStatus().isTerminal()) {
            throw new BusinessRuleException(
                    ("This application is already %s, so it cannot be withdrawn.")
                            .formatted(application.getStatus().candidateFacingLabel()));
        }

        moveOrExplain(application, ApplicationStatus.WITHDRAWN, null, caller.email(),
                request.reason() == null
                        ? "Withdrawn by the candidate"
                        : "Withdrawn by the candidate: " + request.reason());

        auditService.recordEntityChange(AuditAction.UPDATE, "Application", applicationId,
                caller.id(), caller.email(),
                "Candidate withdrew " + ApplicationReference.of(applicationId),
                null, ApplicationStatus.WITHDRAWN.name(), context);

        return selfWithInterview(application);
    }

    // ==================================================================
    // Reading
    // ==================================================================

    @Transactional(readOnly = true)
    public PageResponse<ApplicationDtos.SummaryResponse> list(Long jobId,
                                                              Long candidateId,
                                                              ApplicationStatus status,
                                                              Long assignedToUserId,
                                                              Boolean unassigned,
                                                              LocalDate from,
                                                              LocalDate to,
                                                              String search,
                                                              Pageable pageable) {
        Specification<Application> specification = Specification.<Application>unrestricted()
                .and(SpecificationUtils.notDeleted())
                .and(SpecificationUtils.equal("job.id", jobId))
                .and(SpecificationUtils.equal("candidate.id", candidateId))
                .and(SpecificationUtils.equal("status", status))
                .and(SpecificationUtils.equal("assignedTo.id", assignedToUserId))
                .and(SpecificationUtils.anyContainsIgnoreCase(
                        search, "candidate.fullName", "candidate.email", "candidate.phone"));

        if (Boolean.TRUE.equals(unassigned)) {
            specification = specification.and(
                    (root, query, builder) -> builder.isNull(root.get("assignedTo")));
        }
        if (from != null) {
            specification = specification.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.get("appliedOn"), from));
        }
        if (to != null) {
            specification = specification.and((root, query, builder) ->
                    builder.lessThanOrEqualTo(root.get("appliedOn"), to));
        }

        Page<Application> page = applicationRepository.findAll(specification, pageable);

        Map<Long, Long> interviewCounts = interviewCountsFor(
                page.getContent().stream().map(Application::getId).toList());

        return PageResponse.from(page, application -> {
            ApplicationDtos.SummaryResponse base = mapper.toSummaryResponse(application);
            return new ApplicationDtos.SummaryResponse(
                    base.id(), base.jobId(), base.jobTitle(),
                    base.candidateId(), base.candidateName(), base.candidatePhone(),
                    base.status(), base.appliedOn(), base.daysInPipeline(),
                    base.assignedToUserId(), base.assignedToName(), base.hasResume(),
                    interviewCounts.getOrDefault(application.getId(), 0L).intValue());
        });
    }

    @Transactional(readOnly = true)
    public ApplicationDtos.Response get(Long applicationId) {
        return withInterviewCounts(load(applicationId));
    }

    @Transactional(readOnly = true)
    public ApplicationDtos.DetailResponse getDetail(Long applicationId) {
        Application application = applicationRepository.findActiveWithHistoryById(applicationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Application", applicationId));

        List<ApplicationDtos.StatusHistoryEntry> history = application.getStatusHistory().stream()
                .map(mapper::toHistoryEntry)
                .toList();

        List<InterviewDtos.Response> interviews =
                interviewRepository.findByApplicationId(applicationId).stream()
                        .map(mapper::toResponse)
                        .toList();

        return new ApplicationDtos.DetailResponse(
                withInterviewCounts(application), history, interviews);
    }

    /** The per-status counts a pipeline board renders its columns from. */
    @Transactional(readOnly = true)
    public ApplicationDtos.PipelineSummary pipelineFor(Long jobId) {
        Job job = loadJob(jobId);

        // Every status is present, including the empty ones, so the board can
        // render a complete set of columns without inventing the missing keys.
        Map<ApplicationStatus, Long> counts = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus status : ApplicationStatus.values()) {
            counts.put(status, 0L);
        }

        long total = 0;
        for (ApplicationRepository.StatusCount row
                : applicationRepository.countByStatusForJob(jobId)) {
            counts.put(row.getStatus(), row.getTotal());
            total += row.getTotal();
        }

        long committed = JobService.committedStatuses().stream()
                .mapToLong(status -> counts.getOrDefault(status, 0L))
                .sum();

        return new ApplicationDtos.PipelineSummary(
                jobId, job.getTitle(), job.getOpenings(),
                committed, job.getOpenings() - committed, total, counts);
    }

    @Transactional(readOnly = true)
    public List<ApplicationDtos.SelfResponse> listOwn(AuthenticatedUser caller) {
        // findOwn, not resolveForSelfService: this is a read, and the resolver
        // writes. See CandidateService.findOwn for what that cost.
        return candidateService.findOwn(caller)
                .map(candidate -> applicationRepository.findByCandidateId(candidate.getId())
                        .stream()
                        .map(this::selfWithInterview)
                        .toList())
                // No candidate record means no applications. An empty list is
                // the honest answer, not an error.
                .orElseGet(List::of);
    }

    @Transactional(readOnly = true)
    public ApplicationDtos.SelfResponse getOwn(Long applicationId, AuthenticatedUser caller) {
        return selfWithInterview(loadOwn(applicationId, caller));
    }

    // ==================================================================
    // Used by InterviewService
    // ==================================================================

    /**
     * Moves an application forward because of an interview, on behalf of
     * {@code InterviewService}.
     *
     * <p>Exposed rather than duplicated so the transition rules and the trail
     * write stay in one place. A no-op when the application is already at or
     * past the target.
     */
    @Transactional
    public void advanceForInterview(Application application,
                                    ApplicationStatus target,
                                    AuthenticatedUser actor,
                                    String note) {
        if (application.getStatus() == target) {
            return;
        }
        if (!application.getStatus().canTransitionTo(target)) {
            // Logged rather than thrown: the caller's primary action - booking,
            // cancelling or writing up an interview - has already succeeded and
            // must not be rolled back because the status could not follow.
            //
            // But it must not be silent either. This swallowed the whole
            // cancel-walks-the-status-back feature once already, because
            // INTERVIEW_SCHEDULED -> SHORTLISTED was missing from the state
            // machine and the early return hid it.
            log.warn("Could not move application {} from {} to {} alongside an interview "
                            + "change; {} is not a permitted transition. The interview change "
                            + "stands and the status is unchanged.",
                    application.getId(), application.getStatus(), target, target);
            return;
        }
        application.moveTo(target, actor.id(), actor.email(), note);
    }

    @Transactional(readOnly = true)
    public Application loadActive(Long applicationId) {
        return load(applicationId);
    }

    // ==================================================================
    // Internals
    // ==================================================================

    private Application load(Long applicationId) {
        return applicationRepository.findActiveById(applicationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Application", applicationId));
    }

    /**
     * Loads an application that belongs to the calling candidate.
     *
     * <p>Answers 404 rather than 403 for someone else's application, so the API
     * never confirms that an id exists - the same rule the client portal
     * follows.
     */
    private Application loadOwn(Long applicationId, AuthenticatedUser caller) {
        Application application = load(applicationId);
        Candidate candidate = application.getCandidate();

        // Either the records are linked, or the candidate carries the caller's
        // email - the guest-then-register case, where nothing has linked them
        // yet. Matching on email is safe because it is unique on both tables
        // and adoptOrCreateForUser refuses a cross-link.
        boolean linked = candidate.getUser() != null
                && candidate.getUser().getId().equals(caller.id());
        boolean sameEmail = candidate.getEmail() != null
                && candidate.getEmail().equalsIgnoreCase(caller.email());

        if (!linked && !sameEmail) {
            throw ResourceNotFoundException.of("Application", applicationId);
        }
        return application;
    }

    private Job loadJob(Long jobId) {
        return jobRepository.findActiveById(jobId)
                .orElseThrow(() -> ResourceNotFoundException.of("Job", jobId));
    }

    private Job loadOpenJob(Long jobId) {
        Job job = loadJob(jobId);
        requireAcceptingApplications(job);
        return job;
    }

    private Job loadOpenJobBySlug(String slug) {
        return jobRepository.findPubliclyVisibleBySlug(slug, LocalDate.now())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No open vacancy exists at that address. It may have been filled or "
                                + "withdrawn."));
    }

    private void requireAcceptingApplications(Job job) {
        if (!job.getStatus().acceptsApplications()) {
            throw new BusinessRuleException(
                    ("'%s' is not accepting applications at the moment.")
                            .formatted(job.getTitle()));
        }
        if (job.hasExpiredOn(LocalDate.now())) {
            throw new BusinessRuleException(
                    ("Applications for '%s' closed on %s.")
                            .formatted(job.getTitle(), job.getExpiresOn()));
        }
    }

    /**
     * @param anonymous when true the message avoids confirming the email
     *                  address, since whoever typed it may not own it
     */
    private void requireNotAlreadyApplied(Job job, Candidate candidate, boolean anonymous) {
        if (!applicationRepository.existsForJobAndCandidate(job.getId(), candidate.getId())) {
            return;
        }
        throw new DuplicateResourceException(anonymous
                ? ("An application for '%s' already exists for %s. Check your email for the "
                        + "reference, or sign in to see its progress.")
                        .formatted(job.getTitle(), candidate.maskedEmail())
                : ("%s has already applied for '%s'.")
                        .formatted(candidate.getFullName(), job.getTitle()));
    }

    /**
     * Applies a transition, turning the entity's refusal into a user-facing
     * explanation that names the legal moves.
     */
    private void moveOrExplain(Application application,
                               ApplicationStatus target,
                               Long actorUserId,
                               String actorEmail,
                               String note) {
        try {
            application.moveTo(target, actorUserId, actorEmail, note);
        } catch (IllegalStateException e) {
            ApplicationStatus current = application.getStatus();
            throw new BusinessRuleException(current.isTerminal()
                    ? ("This application is %s, which is final. Re-engaging this candidate "
                            + "means a new application against the job, which stays visible "
                            + "in their history.").formatted(current)
                    : ("An application cannot move from %s to %s. From here it can go to: %s.")
                            .formatted(current, target, current.allowedNextStatuses()));
        }
    }

    private void warnIfOverCommitting(Application application, ApplicationStatus target) {
        if (!target.consumesAnOpening()) {
            return;
        }
        Job job = application.getJob();
        long committed = applicationRepository.countByJobAndStatusIn(
                job.getId(), JobService.committedStatuses());
        if (committed >= job.getOpenings()) {
            // Not refused - over-hiring against a requisition happens and is a
            // commercial decision - but it must not happen silently.
            log.warn("Job {} ('{}') has {} of {} opening(s) already committed; moving "
                            + "application {} to {} over-commits it.",
                    job.getId(), job.getTitle(), committed, job.getOpenings(),
                    application.getId(), target);
        }
    }

    private void attachResumeIfPresent(Application application,
                                       Candidate candidate,
                                       MultipartFile resume) {
        if (resume == null || resume.isEmpty()) {
            // Fall back to whatever CV the candidate already has on file.
            application.setResumeDocument(candidate.getResumeDocument());
            return;
        }

        StoredDocument document = candidateService.storeResume(resume, candidate.getId());
        application.setResumeDocument(document);

        // Only adopt it as the candidate's current CV when the record is not
        // owned by a registered user. See CandidateService's class note.
        if (!candidate.hasLogin() || candidate.getResumeDocument() == null) {
            candidate.setResumeDocument(document);
        }
    }

    private User resolveAssignee(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    }

    private ApplicationDtos.ApplyResponse acknowledgement(Application application, Job job) {
        return new ApplicationDtos.ApplyResponse(
                ApplicationReference.of(application.getId()),
                application.getId(),
                job.getTitle(),
                application.getStatus(),
                "Thank you. Your application has been received and we will be in touch.");
    }

    private ApplicationDtos.Response withInterviewCounts(Application application) {
        ApplicationDtos.Response base = mapper.toResponse(application);
        int count = interviewRepository.findByApplicationId(application.getId()).size();
        boolean upcoming = interviewRepository.hasUpcomingInterview(
                application.getId(), Instant.now());

        return new ApplicationDtos.Response(
                base.id(), base.jobId(), base.jobSlug(), base.jobTitle(), base.categoryName(),
                base.candidateId(), base.candidateName(), base.candidateEmail(),
                base.candidatePhone(),
                base.status(), base.allowedNextStatuses(), base.terminal(),
                base.appliedOn(), base.daysInPipeline(),
                base.source(), base.coverNote(), base.expectedSalary(),
                base.resumeDocumentId(), base.resumeFileName(),
                base.assignedToUserId(), base.assignedToName(), base.rejectionReason(),
                count, upcoming, base.createdAt());
    }

    private ApplicationDtos.SelfResponse selfWithInterview(Application application) {
        ApplicationDtos.SelfResponse base = mapper.toSelfResponse(application);
        Instant next = interviewRepository.findByApplicationId(application.getId()).stream()
                .filter(interview -> interview.isUpcomingAt(Instant.now()))
                .map(Interview::getScheduledAt)
                .min(Instant::compareTo)
                .orElse(null);

        return new ApplicationDtos.SelfResponse(
                base.id(), base.reference(), base.jobId(), base.jobSlug(), base.jobTitle(),
                base.categoryName(), base.location(),
                base.status(), base.statusLabel(), base.appliedOn(),
                base.terminal(), base.withdrawable(), next,
                base.expectedSalary(), base.resumeDocumentId());
    }

    private Map<Long, Long> interviewCountsFor(List<Long> applicationIds) {
        if (applicationIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (InterviewRepository.ApplicationInterviewCount row
                : interviewRepository.countByApplicationIds(applicationIds)) {
            counts.put(row.getApplicationId(), row.getTotal());
        }
        return counts;
    }
}
