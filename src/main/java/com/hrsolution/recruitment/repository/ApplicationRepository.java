package com.hrsolution.recruitment.repository;

import com.hrsolution.recruitment.entity.Application;
import com.hrsolution.recruitment.entity.ApplicationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long>,
        JpaSpecificationExecutor<Application> {

    @EntityGraph(attributePaths = {"job", "job.category", "candidate", "assignedTo", "resumeDocument"})
    @Query("select a from Application a where a.id = :id and a.deleted = false")
    Optional<Application> findActiveById(@Param("id") Long id);

    /** With the funnel trail, for the application detail screen. */
    @EntityGraph(attributePaths = {"job", "candidate", "assignedTo", "statusHistory"})
    @Query("select a from Application a where a.id = :id and a.deleted = false")
    Optional<Application> findActiveWithHistoryById(@Param("id") Long id);

    /**
     * Duplicate check for the apply endpoints.
     *
     * <p>Ignores the soft-delete flag to match the database constraint, which
     * also spans deleted rows. Returning false here for a soft-deleted
     * application would turn a clean 409 into a 500 from the constraint.
     */
    @Query("""
            select count(a) > 0 from Application a
            where a.job.id = :jobId and a.candidate.id = :candidateId
            """)
    boolean existsForJobAndCandidate(@Param("jobId") Long jobId,
                                     @Param("candidateId") Long candidateId);

    @EntityGraph(attributePaths = {"job", "job.category", "resumeDocument"})
    @Query("""
            select a from Application a
            where a.candidate.id = :candidateId and a.deleted = false
            order by a.appliedOn desc, a.id desc
            """)
    List<Application> findByCandidateId(@Param("candidateId") Long candidateId);

    @Query("""
            select count(a) from Application a
            where a.job.id = :jobId and a.deleted = false and a.status in :statuses
            """)
    long countByJobAndStatusIn(@Param("jobId") Long jobId,
                               @Param("statuses") Collection<ApplicationStatus> statuses);

    long countByCandidateIdAndDeletedFalse(Long candidateId);

    /**
     * Application counts for a whole page of candidates in one query.
     *
     * <p>Counting per row by loading each candidate's applications turned a
     * 20-row page into 20 collection fetches. This makes it one aggregate.
     */
    @Query("""
            select a.candidate.id as candidateId, count(a) as total from Application a
            where a.candidate.id in :candidateIds and a.deleted = false
            group by a.candidate.id
            """)
    List<CandidateApplicationCount> countByCandidateIds(
            @Param("candidateIds") Collection<Long> candidateIds);

    /** Projection for the batched candidate-list counts. */
    interface CandidateApplicationCount {
        Long getCandidateId();

        long getTotal();
    }

    /** Whether this candidate has ever reached JOINED, without loading the rows. */
    @Query("""
            select count(a) > 0 from Application a
            where a.candidate.id = :candidateId
              and a.deleted = false
              and a.status = com.hrsolution.recruitment.entity.ApplicationStatus.JOINED
            """)
    boolean hasJoinedApplication(@Param("candidateId") Long candidateId);

    @Query("""
            select a.status as status, count(a) as total from Application a
            where a.job.id = :jobId and a.deleted = false
            group by a.status
            """)
    List<StatusCount> countByStatusForJob(@Param("jobId") Long jobId);

    /**
     * Per-job, per-status counts for a whole page of jobs in one query.
     *
     * <p>The job list needs a total and a committed count for every row. Asking
     * per row turned a 20-row page into 41 queries; this makes it two. Callers
     * aggregate the rows in memory, which is trivial for a page.
     */
    @Query("""
            select a.job.id as jobId, a.status as status, count(a) as total from Application a
            where a.job.id in :jobIds and a.deleted = false
            group by a.job.id, a.status
            """)
    List<JobStatusCount> countByStatusForJobs(@Param("jobIds") Collection<Long> jobIds);

    /** Projection for the pipeline summary on a job. */
    interface StatusCount {
        ApplicationStatus getStatus();

        long getTotal();
    }

    /** Projection for the batched job-list counts. */
    interface JobStatusCount {
        Long getJobId();

        ApplicationStatus getStatus();

        long getTotal();
    }
}
