package com.hrsolution.recruitment.mapper;

import com.hrsolution.recruitment.ApplicationReference;
import com.hrsolution.recruitment.dto.ApplicationDtos;
import com.hrsolution.recruitment.dto.CandidateDtos;
import com.hrsolution.recruitment.dto.InterviewDtos;
import com.hrsolution.recruitment.dto.JobDtos;
import com.hrsolution.recruitment.entity.Application;
import com.hrsolution.recruitment.entity.ApplicationStatusHistory;
import com.hrsolution.recruitment.entity.Candidate;
import com.hrsolution.recruitment.entity.Interview;
import com.hrsolution.recruitment.entity.Job;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Entity to DTO mapping for the whole recruitment module.
 *
 * <p><strong>No plain helper methods on this interface.</strong> MapStruct
 * treats any non-private method on a mapper as a candidate type conversion for
 * its signature, so a {@code String f(Long)} helper would be applied to every
 * Long-to-String mapping here - the trap that silently corrupted 136 fields in
 * Phase 3. Anything needing a helper calls a static method through
 * {@code imports}, and {@code MapperHelperLeakTest} asserts the generated
 * implementations stay clean.
 *
 * <p>Counts, derived openings and the interview flags all depend on data
 * outside the entity being mapped, so the services set them after mapping
 * rather than this interface guessing.
 */
@Mapper(imports = {ApplicationReference.class, java.time.LocalDate.class, java.time.Instant.class})
public interface RecruitmentMapper {

    // ==================================================================
    // Jobs
    // ==================================================================

    @Mapping(target = "categoryId", expression = "java(entity.getCategory().getId())")
    @Mapping(target = "categoryName", expression = "java(entity.getCategory().getName())")
    @Mapping(target = "skillLevel", expression = "java(entity.getCategory().getSkillLevel())")
    @Mapping(target = "clientId",
            expression = "java(entity.getClient() == null ? null : entity.getClient().getId())")
    @Mapping(target = "clientName",
            expression = "java(entity.getClient() == null ? null : entity.getClient().displayName())")
    @Mapping(target = "siteId",
            expression = "java(entity.getSite() == null ? null : entity.getSite().getId())")
    @Mapping(target = "siteName",
            expression = "java(entity.getSite() == null ? null : entity.getSite().getSiteName())")
    @Mapping(target = "salaryRangeLabel", expression = "java(entity.salaryRangeLabel())")
    @Mapping(target = "experienceLabel", expression = "java(entity.experienceLabel())")
    @Mapping(target = "expired", expression = "java(entity.hasExpiredOn(LocalDate.now()))")
    @Mapping(target = "daysUntilExpiry", expression = "java(entity.daysUntilExpiry(LocalDate.now()))")
    @Mapping(target = "publiclyVisible", expression = "java(entity.isOpenOn(LocalDate.now()))")
    // Set by JobService from the applications table.
    @Mapping(target = "openingsCommitted", ignore = true)
    @Mapping(target = "openingsRemaining", ignore = true)
    @Mapping(target = "applicationCount", ignore = true)
    JobDtos.Response toResponse(Job entity);

    @Mapping(target = "categoryName", expression = "java(entity.getCategory().getName())")
    @Mapping(target = "expired", expression = "java(entity.hasExpiredOn(LocalDate.now()))")
    @Mapping(target = "publiclyVisible", expression = "java(entity.isOpenOn(LocalDate.now()))")
    @Mapping(target = "applicationCount", ignore = true)
    JobDtos.SummaryResponse toSummaryResponse(Job entity);

    /**
     * The public shape. No client, no site, no status - see {@link JobDtos} for
     * why those are structurally absent rather than ignored here.
     */
    @Mapping(target = "categoryName", expression = "java(entity.getCategory().getName())")
    @Mapping(target = "skillLevelLabel",
            expression = "java(entity.getCategory().getSkillLevel().getDisplayName())")
    @Mapping(target = "salaryRangeLabel", expression = "java(entity.salaryRangeLabel())")
    @Mapping(target = "experienceLabel", expression = "java(entity.experienceLabel())")
    JobDtos.PublicResponse toPublicResponse(Job entity);

    @Mapping(target = "categoryName", expression = "java(entity.getCategory().getName())")
    @Mapping(target = "salaryRangeLabel", expression = "java(entity.salaryRangeLabel())")
    @Mapping(target = "experienceLabel", expression = "java(entity.experienceLabel())")
    JobDtos.PublicSummaryResponse toPublicSummaryResponse(Job entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    // Resolved from ids by the service, which validates they exist and relate.
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "client", ignore = true)
    @Mapping(target = "site", ignore = true)
    // Has its own endpoint and its own audit event.
    @Mapping(target = "status", ignore = true)
    // Primitives with defaults: a null in the request must not zero them.
    @Mapping(target = "openings", ignore = true)
    @Mapping(target = "displayOrder", ignore = true)
    void applyRequest(JobDtos.Request request, @MappingTarget Job entity);

    // ==================================================================
    // Candidates
    // ==================================================================

    @Mapping(target = "experienceLabel", expression = "java(entity.experienceLabel())")
    @Mapping(target = "hasLogin", expression = "java(entity.hasLogin())")
    @Mapping(target = "userId",
            expression = "java(entity.getUser() == null ? null : entity.getUser().getId())")
    @Mapping(target = "preferredCategoryId",
            expression = "java(entity.getPreferredCategory() == null ? null "
                    + ": entity.getPreferredCategory().getId())")
    @Mapping(target = "preferredCategoryName",
            expression = "java(entity.getPreferredCategory() == null ? null "
                    + ": entity.getPreferredCategory().getName())")
    @Mapping(target = "resumeDocumentId",
            expression = "java(entity.getResumeDocument() == null ? null "
                    + ": entity.getResumeDocument().getId())")
    @Mapping(target = "resumeFileName",
            expression = "java(entity.getResumeDocument() == null ? null "
                    + ": entity.getResumeDocument().getOriginalFileName())")
    @Mapping(target = "applicationCount", ignore = true)
    CandidateDtos.Response toResponse(Candidate entity);

    @Mapping(target = "experienceLabel", expression = "java(entity.experienceLabel())")
    @Mapping(target = "preferredCategoryName",
            expression = "java(entity.getPreferredCategory() == null ? null "
                    + ": entity.getPreferredCategory().getName())")
    @Mapping(target = "hasLogin", expression = "java(entity.hasLogin())")
    @Mapping(target = "hasResume", expression = "java(entity.hasResume())")
    @Mapping(target = "applicationCount", ignore = true)
    CandidateDtos.SummaryResponse toSummaryResponse(Candidate entity);

    /** The candidate's own view: no notes, no spam flags, no source. */
    @Mapping(target = "experienceLabel", expression = "java(entity.experienceLabel())")
    @Mapping(target = "preferredCategoryId",
            expression = "java(entity.getPreferredCategory() == null ? null "
                    + ": entity.getPreferredCategory().getId())")
    @Mapping(target = "preferredCategoryName",
            expression = "java(entity.getPreferredCategory() == null ? null "
                    + ": entity.getPreferredCategory().getName())")
    @Mapping(target = "resumeDocumentId",
            expression = "java(entity.getResumeDocument() == null ? null "
                    + ": entity.getResumeDocument().getId())")
    @Mapping(target = "resumeFileName",
            expression = "java(entity.getResumeDocument() == null ? null "
                    + ": entity.getResumeDocument().getOriginalFileName())")
    @Mapping(target = "applicationCount", ignore = true)
    CandidateDtos.SelfResponse toSelfResponse(Candidate entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "preferredCategory", ignore = true)
    @Mapping(target = "resumeDocument", ignore = true)
    @Mapping(target = "source", ignore = true)
    // Consent is a transition with a timestamp, not a field copy.
    @Mapping(target = "consentGiven", ignore = true)
    @Mapping(target = "consentGivenAt", ignore = true)
    @Mapping(target = "spamSuspected", ignore = true)
    @Mapping(target = "spamReason", ignore = true)
    @Mapping(target = "email", ignore = true)
    void applyRequest(CandidateDtos.Request request, @MappingTarget Candidate entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "preferredCategory", ignore = true)
    @Mapping(target = "resumeDocument", ignore = true)
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "consentGiven", ignore = true)
    @Mapping(target = "consentGivenAt", ignore = true)
    @Mapping(target = "spamSuspected", ignore = true)
    @Mapping(target = "spamReason", ignore = true)
    @Mapping(target = "email", ignore = true)
    // A candidate cannot write their own internal notes.
    @Mapping(target = "notes", ignore = true)
    void applySelfRequest(CandidateDtos.SelfRequest request, @MappingTarget Candidate entity);

    // ==================================================================
    // Applications
    // ==================================================================

    @Mapping(target = "jobId", expression = "java(entity.getJob().getId())")
    @Mapping(target = "jobSlug", expression = "java(entity.getJob().getSlug())")
    @Mapping(target = "jobTitle", expression = "java(entity.getJob().getTitle())")
    @Mapping(target = "categoryName",
            expression = "java(entity.getJob().getCategory().getName())")
    @Mapping(target = "candidateId", expression = "java(entity.getCandidate().getId())")
    @Mapping(target = "candidateName", expression = "java(entity.getCandidate().getFullName())")
    @Mapping(target = "candidateEmail", expression = "java(entity.getCandidate().getEmail())")
    @Mapping(target = "candidatePhone", expression = "java(entity.getCandidate().getPhone())")
    @Mapping(target = "allowedNextStatuses",
            expression = "java(java.util.List.copyOf(entity.getStatus().allowedNextStatuses()))")
    @Mapping(target = "terminal", expression = "java(entity.getStatus().isTerminal())")
    @Mapping(target = "daysInPipeline", expression = "java(entity.daysInPipeline(LocalDate.now()))")
    @Mapping(target = "resumeDocumentId",
            expression = "java(entity.effectiveResume() == null ? null "
                    + ": entity.effectiveResume().getId())")
    @Mapping(target = "resumeFileName",
            expression = "java(entity.effectiveResume() == null ? null "
                    + ": entity.effectiveResume().getOriginalFileName())")
    @Mapping(target = "assignedToUserId",
            expression = "java(entity.getAssignedTo() == null ? null "
                    + ": entity.getAssignedTo().getId())")
    @Mapping(target = "assignedToName",
            expression = "java(entity.getAssignedTo() == null ? null "
                    + ": entity.getAssignedTo().fullName())")
    // Both need the interviews table, so InterviewService supplies them.
    @Mapping(target = "interviewCount", ignore = true)
    @Mapping(target = "hasUpcomingInterview", ignore = true)
    ApplicationDtos.Response toResponse(Application entity);

    @Mapping(target = "jobId", expression = "java(entity.getJob().getId())")
    @Mapping(target = "jobTitle", expression = "java(entity.getJob().getTitle())")
    @Mapping(target = "candidateId", expression = "java(entity.getCandidate().getId())")
    @Mapping(target = "candidateName", expression = "java(entity.getCandidate().getFullName())")
    @Mapping(target = "candidatePhone", expression = "java(entity.getCandidate().getPhone())")
    @Mapping(target = "daysInPipeline", expression = "java(entity.daysInPipeline(LocalDate.now()))")
    @Mapping(target = "assignedToUserId",
            expression = "java(entity.getAssignedTo() == null ? null "
                    + ": entity.getAssignedTo().getId())")
    @Mapping(target = "assignedToName",
            expression = "java(entity.getAssignedTo() == null ? null "
                    + ": entity.getAssignedTo().fullName())")
    @Mapping(target = "hasResume", expression = "java(entity.effectiveResume() != null)")
    @Mapping(target = "interviewCount", ignore = true)
    ApplicationDtos.SummaryResponse toSummaryResponse(Application entity);

    /** The candidate's own view. No assignee, no internal notes, no feedback. */
    @Mapping(target = "reference", expression = "java(ApplicationReference.of(entity.getId()))")
    @Mapping(target = "jobId", expression = "java(entity.getJob().getId())")
    @Mapping(target = "jobSlug", expression = "java(entity.getJob().getSlug())")
    @Mapping(target = "jobTitle", expression = "java(entity.getJob().getTitle())")
    @Mapping(target = "categoryName",
            expression = "java(entity.getJob().getCategory().getName())")
    @Mapping(target = "location", expression = "java(entity.getJob().getLocation())")
    @Mapping(target = "statusLabel",
            expression = "java(entity.getStatus().candidateFacingLabel())")
    @Mapping(target = "terminal", expression = "java(entity.getStatus().isTerminal())")
    @Mapping(target = "withdrawable",
            expression = "java(entity.getStatus().canTransitionTo("
                    + "com.hrsolution.recruitment.entity.ApplicationStatus.WITHDRAWN))")
    @Mapping(target = "resumeDocumentId",
            expression = "java(entity.effectiveResume() == null ? null "
                    + ": entity.effectiveResume().getId())")
    // Needs the interviews table.
    @Mapping(target = "nextInterviewAt", ignore = true)
    ApplicationDtos.SelfResponse toSelfResponse(Application entity);

    @Mapping(target = "madeByStaff", expression = "java(entity.madeByStaff())")
    ApplicationDtos.StatusHistoryEntry toHistoryEntry(ApplicationStatusHistory entity);

    // ==================================================================
    // Interviews
    // ==================================================================

    @Mapping(target = "applicationId", expression = "java(entity.getApplication().getId())")
    @Mapping(target = "candidateName",
            expression = "java(entity.getApplication().getCandidate().getFullName())")
    @Mapping(target = "jobTitle",
            expression = "java(entity.getApplication().getJob().getTitle())")
    @Mapping(target = "interviewerUserId",
            expression = "java(entity.getInterviewer() == null ? null "
                    + ": entity.getInterviewer().getId())")
    @Mapping(target = "interviewerName",
            expression = "java(entity.getInterviewer() == null ? null "
                    + ": entity.getInterviewer().fullName())")
    @Mapping(target = "upcoming", expression = "java(entity.isUpcomingAt(Instant.now()))")
    @Mapping(target = "awaitingFeedback",
            expression = "java(entity.isAwaitingFeedback(Instant.now()))")
    InterviewDtos.Response toResponse(Interview entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "application", ignore = true)
    @Mapping(target = "interviewer", ignore = true)
    // Result, rating and feedback have their own endpoint.
    @Mapping(target = "result", ignore = true)
    @Mapping(target = "rating", ignore = true)
    @Mapping(target = "feedback", ignore = true)
    // Allocated by the service when the request omits it.
    @Mapping(target = "roundNumber", ignore = true)
    void applyRequest(InterviewDtos.Request request, @MappingTarget Interview entity);
}
