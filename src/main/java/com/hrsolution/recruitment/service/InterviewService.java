package com.hrsolution.recruitment.service;

import com.hrsolution.audit.entity.AuditAction;
import com.hrsolution.audit.service.AuditService;
import com.hrsolution.common.error.BusinessRuleException;
import com.hrsolution.common.error.ResourceNotFoundException;
import com.hrsolution.common.repository.SpecificationUtils;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.web.PageResponse;
import com.hrsolution.common.web.RequestContext;
import com.hrsolution.recruitment.ApplicationReference;
import com.hrsolution.recruitment.dto.InterviewDtos;
import com.hrsolution.recruitment.entity.Application;
import com.hrsolution.recruitment.entity.ApplicationStatus;
import com.hrsolution.recruitment.entity.Interview;
import com.hrsolution.recruitment.entity.InterviewMode;
import com.hrsolution.recruitment.entity.InterviewResult;
import com.hrsolution.recruitment.mapper.RecruitmentMapper;
import com.hrsolution.recruitment.repository.InterviewRepository;
import com.hrsolution.user.entity.User;
import com.hrsolution.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Interviews.
 *
 * <p>Scheduling an interview moves the application to
 * {@code INTERVIEW_SCHEDULED} as a side effect, and that is deliberate: a
 * recruiter who has just booked a slot should not then have to remember to
 * drag the card. The reverse is blocked - {@code ApplicationService} refuses a
 * manual move to {@code INTERVIEW_SCHEDULED} with no interview behind it, so
 * the status cannot lie about whether a slot exists.
 *
 * <p>Recording a {@code PASS} can advance the application to
 * {@code SELECTED} in the same call, but only when asked. Defaulting to it
 * would be wrong: passing round one of three is not a decision to hire.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewService {

    /** How far either side of a slot counts as a clash worth warning about. */
    private static final Duration CLASH_WINDOW = Duration.ofHours(4);

    /**
     * How far ahead of a slot its outcome may still be recorded.
     *
     * <p>Covers an interview that finished early. See {@link #recordResult}.
     */
    private static final Duration EARLY_WRITE_UP_GRACE = Duration.ofHours(2);

    private final InterviewRepository interviewRepository;
    private final UserRepository userRepository;
    private final ApplicationService applicationService;
    private final RecruitmentMapper mapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<InterviewDtos.Response> listForApplication(Long applicationId) {
        // Confirms the application exists, so an unknown id is a 404 rather
        // than a misleading empty list.
        applicationService.loadActive(applicationId);
        return interviewRepository.findByApplicationId(applicationId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public InterviewDtos.Response get(Long interviewId) {
        return mapper.toResponse(load(interviewId));
    }

    /**
     * The interviewer's own schedule, and the "awaiting feedback" queue.
     *
     * @param awaitingFeedback when true, only slots that have passed with no
     *                         result - the list that matters most, because an
     *                         interview nobody wrote up is a candidate left
     *                         waiting
     */
    @Transactional(readOnly = true)
    public PageResponse<InterviewDtos.Response> list(Long interviewerUserId,
                                                     InterviewResult result,
                                                     Instant from,
                                                     Instant to,
                                                     Boolean awaitingFeedback,
                                                     Pageable pageable) {
        Specification<Interview> specification = Specification.<Interview>unrestricted()
                .and(SpecificationUtils.notDeleted())
                .and(SpecificationUtils.equal("interviewer.id", interviewerUserId))
                .and(SpecificationUtils.equal("result", result));

        if (from != null) {
            specification = specification.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.get("scheduledAt"), from));
        }
        if (to != null) {
            specification = specification.and((root, query, builder) ->
                    builder.lessThan(root.get("scheduledAt"), to));
        }
        if (Boolean.TRUE.equals(awaitingFeedback)) {
            Instant now = Instant.now();
            specification = specification
                    .and(SpecificationUtils.equal("result", InterviewResult.PENDING))
                    .and((root, query, builder) ->
                            builder.lessThanOrEqualTo(root.get("scheduledAt"), now));
        }

        Page<Interview> page = interviewRepository.findAll(specification, pageable);
        return PageResponse.from(page, mapper::toResponse);
    }

    @Transactional
    public InterviewDtos.Response schedule(Long applicationId,
                                           InterviewDtos.Request request,
                                           AuthenticatedUser actor,
                                           RequestContext context) {
        Application application = applicationService.loadActive(applicationId);

        if (application.getStatus().isTerminal()) {
            throw new BusinessRuleException(
                    ("This application is %s, so no interview can be scheduled against it.")
                            .formatted(application.getStatus()));
        }
        if (!application.acceptsInterviewScheduling()) {
            throw new BusinessRuleException(
                    ("An application at %s is not ready for an interview. Move it to SCREENING "
                            + "or SHORTLISTED first.").formatted(application.getStatus()));
        }
        requireFutureSlot(request.scheduledAt());
        requireVenue(request.mode(), request.venueOrLink());

        int round = request.roundNumber() != null
                ? request.roundNumber()
                : interviewRepository.findHighestRoundNumber(applicationId) + 1;

        if (request.roundNumber() != null
                && interviewRepository.findByApplicationId(applicationId).stream()
                        .anyMatch(existing -> existing.getRoundNumber() == round)) {
            throw new BusinessRuleException(
                    ("Round %d already exists on this application. Omit roundNumber to append "
                            + "the next round, or update the existing one.").formatted(round));
        }

        Interview interview = new Interview();
        mapper.applyRequest(request, interview);
        interview.setApplication(application);
        interview.setRoundNumber(round);
        interview.setInterviewer(resolveInterviewer(request.interviewerUserId()));
        interview.setResult(InterviewResult.PENDING);

        warnOnClash(interview);

        Interview saved = interviewRepository.save(interview);

        // Booking a slot is what INTERVIEW_SCHEDULED means, so the application
        // follows automatically rather than waiting to be dragged.
        applicationService.advanceForInterview(
                application, ApplicationStatus.INTERVIEW_SCHEDULED, actor,
                "Round %d interview scheduled for %s".formatted(round, saved.getScheduledAt()));

        auditService.recordEntityChange(AuditAction.CREATE, "Interview", saved.getId(),
                actor.id(), actor.email(),
                "Scheduled round %d %s interview on %s for %s (%s)".formatted(
                        round, saved.getMode(), saved.getScheduledAt(),
                        application.getCandidate().getFullName(),
                        ApplicationReference.of(applicationId)),
                null, saved.getScheduledAt().toString(), context);

        return mapper.toResponse(saved);
    }

    /**
     * Reschedules or corrects an interview.
     *
     * <p>Refused once a result is recorded. Moving the date on a completed
     * interview would make the feedback refer to a slot that never happened,
     * and the write-up is evidence that the candidate was assessed.
     */
    @Transactional
    public InterviewDtos.Response update(Long interviewId,
                                         InterviewDtos.Request request,
                                         AuthenticatedUser actor,
                                         RequestContext context) {
        Interview interview = load(interviewId);

        if (interview.getResult().isDecided()) {
            throw new BusinessRuleException(
                    ("This interview is already recorded as %s, so its details can no longer be "
                            + "changed. Schedule another round instead.")
                            .formatted(interview.getResult()));
        }
        requireFutureSlot(request.scheduledAt());
        requireVenue(request.mode(), request.venueOrLink());

        Instant previousSlot = interview.getScheduledAt();

        mapper.applyRequest(request, interview);
        interview.setInterviewer(resolveInterviewer(request.interviewerUserId()));

        warnOnClash(interview);

        auditService.recordEntityChange(AuditAction.UPDATE, "Interview", interviewId,
                actor.id(), actor.email(),
                "Rescheduled round %d interview from %s to %s".formatted(
                        interview.getRoundNumber(), previousSlot, interview.getScheduledAt()),
                String.valueOf(previousSlot), String.valueOf(interview.getScheduledAt()), context);

        return mapper.toResponse(interview);
    }

    /**
     * Records the outcome.
     *
     * <p>{@code advanceApplication} moves a passed application to
     * {@code SELECTED}. Not the default, because passing one round of three is
     * not a hiring decision - and because {@code SELECTED} commits an opening.
     */
    @Transactional
    public InterviewDtos.Response recordResult(Long interviewId,
                                               InterviewDtos.ResultRequest request,
                                               AuthenticatedUser actor,
                                               RequestContext context) {
        Interview interview = load(interviewId);

        if (request.result() == InterviewResult.PENDING) {
            throw new BusinessRuleException(
                    "PENDING is the starting state, not an outcome. Record PASS, FAIL or "
                            + "NO_SHOW.");
        }
        if (interview.getResult().isDecided()) {
            throw new BusinessRuleException(
                    ("This interview is already recorded as %s. A recorded outcome cannot be "
                            + "revised - schedule another round if the assessment has changed.")
                            .formatted(interview.getResult()));
        }
        // Deliberately a grace window rather than "must be in the past". An
        // interview that starts on time and finishes early is written up before
        // its scheduled end, and a panel that wrapped up fifteen minutes ahead
        // should not have to reschedule the slot to record what happened.
        //
        // What this still catches is writing up next week's interview, which is
        // almost always the wrong row rather than a prescient interviewer.
        Instant tooEarly = Instant.now().plus(EARLY_WRITE_UP_GRACE);
        if (interview.getScheduledAt() != null && interview.getScheduledAt().isAfter(tooEarly)) {
            throw new BusinessRuleException(
                    ("This interview is scheduled for %s, which is more than %d minutes away, so "
                            + "it cannot have happened yet. Check you have the right round.")
                            .formatted(interview.getScheduledAt(),
                                    EARLY_WRITE_UP_GRACE.toMinutes()));
        }

        InterviewResult previous = interview.getResult();
        interview.setResult(request.result());
        interview.setRating(request.rating());
        interview.setFeedback(request.feedback());

        Application application = interview.getApplication();

        if (request.result() == InterviewResult.PASS
                && Boolean.TRUE.equals(request.advanceApplication())) {
            applicationService.advanceForInterview(
                    application, ApplicationStatus.SELECTED, actor,
                    "Passed round %d interview".formatted(interview.getRoundNumber()));
        }

        // A failure or a no-show does NOT auto-reject. Rejecting needs a reason
        // and is a decision a person takes - a candidate who fails a technical
        // round may still suit another role, and ON_HOLD exists for exactly
        // that.
        if (request.result() != InterviewResult.PASS) {
            log.info("Interview {} recorded as {} for {}. The application stays at {} - "
                            + "reject or hold it explicitly.",
                    interviewId, request.result(),
                    ApplicationReference.of(application.getId()), application.getStatus());
        }

        auditService.recordEntityChange(AuditAction.UPDATE, "Interview", interviewId,
                actor.id(), actor.email(),
                "Round %d interview for %s recorded as %s%s".formatted(
                        interview.getRoundNumber(),
                        ApplicationReference.of(application.getId()),
                        request.result(),
                        request.rating() == null ? "" : " (rated " + request.rating() + "/5)"),
                previous.name(), request.result().name(), context);

        return mapper.toResponse(interview);
    }

    /**
     * Cancels a scheduled interview.
     *
     * <p>Soft delete, and refused once a result is recorded: the write-up is
     * the evidence that the candidate was assessed, which Phase 10 may be asked
     * to produce.
     */
    @Transactional
    public void cancel(Long interviewId, AuthenticatedUser actor, RequestContext context) {
        Interview interview = load(interviewId);

        if (interview.getResult().isDecided()) {
            throw new BusinessRuleException(
                    ("This interview is recorded as %s, so it cannot be cancelled - it happened. "
                            + "Its feedback is the record that the candidate was assessed.")
                            .formatted(interview.getResult()));
        }

        interview.markDeleted(actor.email());

        Application application = interview.getApplication();
        // The application claimed INTERVIEW_SCHEDULED because of this slot. If
        // nothing else is booked, that is no longer true, so walk it back to
        // SHORTLISTED rather than leave the status asserting a meeting that is
        // not going to happen.
        if (application.getStatus() == ApplicationStatus.INTERVIEW_SCHEDULED
                && !interviewRepository.hasUpcomingInterview(
                        application.getId(), Instant.now())) {
            applicationService.advanceForInterview(
                    application, ApplicationStatus.SHORTLISTED, actor,
                    "Interview cancelled; no other interview is scheduled");
        }

        auditService.recordEntityChange(AuditAction.DELETE, "Interview", interviewId,
                actor.id(), actor.email(),
                "Cancelled round %d interview for %s".formatted(
                        interview.getRoundNumber(),
                        ApplicationReference.of(application.getId())),
                null, null, context);
    }

    // ==================================================================
    // Internals
    // ==================================================================

    private Interview load(Long interviewId) {
        return interviewRepository.findActiveById(interviewId)
                .orElseThrow(() -> ResourceNotFoundException.of("Interview", interviewId));
    }

    private void requireFutureSlot(Instant scheduledAt) {
        if (scheduledAt != null && scheduledAt.isBefore(Instant.now())) {
            throw new BusinessRuleException(
                    "An interview cannot be scheduled in the past. Record the outcome of a past "
                            + "interview instead.");
        }
    }

    /**
     * A candidate cannot attend an interview with no location.
     *
     * <p>PHONE is exempt: the number is on the candidate's record, so there is
     * nothing extra to state.
     */
    private void requireVenue(InterviewMode mode, String venueOrLink) {
        if (mode == null || mode == InterviewMode.PHONE) {
            return;
        }
        if (venueOrLink == null || venueOrLink.isBlank()) {
            throw new BusinessRuleException(mode.needsALink()
                    ? "A joining link is required for a video interview."
                    : "An address is required for an in-person interview.");
        }
    }

    private User resolveInterviewer(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    }

    /**
     * Warns, rather than refuses, when the interviewer is double-booked.
     *
     * <p>A panel sitting in on two candidates back to back with a slight
     * overlap is a scheduling decision, not an error, so this must not block.
     * But a recruiter booking into an occupied slot by accident needs to find
     * out before the candidate does.
     */
    private void warnOnClash(Interview interview) {
        if (interview.getInterviewer() == null || interview.getScheduledAt() == null) {
            return;
        }
        List<Interview> nearby = interviewRepository.findForInterviewerBetween(
                interview.getInterviewer().getId(),
                interview.getScheduledAt().minus(CLASH_WINDOW),
                interview.getScheduledAt().plus(CLASH_WINDOW));

        nearby.stream()
                .filter(other -> !other.getId().equals(interview.getId()))
                .filter(interview::overlaps)
                .forEach(clash -> log.warn(
                        "Interviewer {} is already booked at {} (interview {}). The new slot at "
                                + "{} overlaps it.",
                        interview.getInterviewer().getEmail(), clash.getScheduledAt(),
                        clash.getId(), interview.getScheduledAt()));
    }
}
