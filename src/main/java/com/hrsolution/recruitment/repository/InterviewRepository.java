package com.hrsolution.recruitment.repository;

import com.hrsolution.recruitment.entity.Interview;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InterviewRepository extends JpaRepository<Interview, Long>,
        JpaSpecificationExecutor<Interview> {

    @EntityGraph(attributePaths = {"application", "application.candidate", "application.job",
            "interviewer"})
    @Query("select i from Interview i where i.id = :id and i.deleted = false")
    Optional<Interview> findActiveById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"interviewer"})
    @Query("""
            select i from Interview i
            where i.application.id = :applicationId and i.deleted = false
            order by i.roundNumber asc
            """)
    List<Interview> findByApplicationId(@Param("applicationId") Long applicationId);

    @Query("""
            select coalesce(max(i.roundNumber), 0) from Interview i
            where i.application.id = :applicationId
            """)
    int findHighestRoundNumber(@Param("applicationId") Long applicationId);

    /**
     * Interviews for one interviewer in a window, for the double-booking warning.
     *
     * <p>Takes the window rather than filtering in memory because an
     * interviewer with two years of history should not be loaded to schedule
     * one slot.
     */
    @Query("""
            select i from Interview i
            where i.interviewer.id = :interviewerId
              and i.deleted = false
              and i.scheduledAt >= :from
              and i.scheduledAt < :to
            """)
    List<Interview> findForInterviewerBetween(@Param("interviewerId") Long interviewerId,
                                              @Param("from") Instant from,
                                              @Param("to") Instant to);

    @Query("""
            select count(i) > 0 from Interview i
            where i.application.id = :applicationId
              and i.deleted = false
              and i.result = com.hrsolution.recruitment.entity.InterviewResult.PENDING
              and i.scheduledAt > :now
            """)
    boolean hasUpcomingInterview(@Param("applicationId") Long applicationId,
                                 @Param("now") Instant now);

    /**
     * Interview counts for a whole page of applications in one query, so the
     * pipeline board does not issue a count per card.
     */
    @Query("""
            select i.application.id as applicationId, count(i) as total from Interview i
            where i.application.id in :applicationIds and i.deleted = false
            group by i.application.id
            """)
    List<ApplicationInterviewCount> countByApplicationIds(
            @Param("applicationIds") Collection<Long> applicationIds);

    /** Projection for the batched board counts. */
    interface ApplicationInterviewCount {
        Long getApplicationId();

        long getTotal();
    }
}
