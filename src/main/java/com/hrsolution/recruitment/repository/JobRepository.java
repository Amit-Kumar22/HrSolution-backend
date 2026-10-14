package com.hrsolution.recruitment.repository;

import com.hrsolution.recruitment.entity.Job;
import com.hrsolution.recruitment.entity.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long>,
        JpaSpecificationExecutor<Job> {

    @EntityGraph(attributePaths = {"category", "client", "site"})
    @Query("select j from Job j where j.id = :id and j.deleted = false")
    Optional<Job> findActiveById(@Param("id") Long id);

    /**
     * The public lookup: by slug, open, and not lapsed.
     *
     * <p>Expiry is in the query rather than checked afterwards so that a lapsed
     * advert is a 404 at the public URL, not a 200 the caller has to interpret.
     * Note {@code client} is deliberately absent from the graph - the public
     * response has no client field, and not fetching it is one less chance to
     * leak it.
     */
    @EntityGraph(attributePaths = {"category"})
    @Query("""
            select j from Job j
            where j.slug = :slug
              and j.deleted = false
              and j.status = com.hrsolution.recruitment.entity.JobStatus.OPEN
              and (j.expiresOn is null or j.expiresOn >= :onDate)
            """)
    Optional<Job> findPubliclyVisibleBySlug(@Param("slug") String slug,
                                            @Param("onDate") LocalDate onDate);

    @EntityGraph(attributePaths = {"category"})
    @Query("""
            select j from Job j
            where j.deleted = false
              and j.status = com.hrsolution.recruitment.entity.JobStatus.OPEN
              and (j.expiresOn is null or j.expiresOn >= :onDate)
              and (:categoryId is null or j.category.id = :categoryId)
              and (:city is null or lower(j.city) = lower(:city))
            """)
    Page<Job> findPubliclyVisible(@Param("onDate") LocalDate onDate,
                                  @Param("categoryId") Long categoryId,
                                  @Param("city") String city,
                                  Pageable pageable);

    /** Slug uniqueness ignores the soft-delete flag, because the DB constraint does. */
    @Query("select count(j) > 0 from Job j where j.slug = :slug")
    boolean existsBySlugIgnoringSoftDelete(@Param("slug") String slug);

    @Query("select count(j) > 0 from Job j where j.slug = :slug and j.id <> :excludeId")
    boolean existsBySlugExcluding(@Param("slug") String slug, @Param("excludeId") Long excludeId);

    long countByStatusAndDeletedFalse(JobStatus status);
}
