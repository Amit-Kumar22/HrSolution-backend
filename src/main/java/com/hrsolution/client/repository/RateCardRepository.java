package com.hrsolution.client.repository;

import com.hrsolution.client.entity.RateCard;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RateCardRepository extends JpaRepository<RateCard, Long>,
        JpaSpecificationExecutor<RateCard> {

    @EntityGraph(attributePaths = {"client", "category", "site"})
    @Query("select r from RateCard r where r.id = :id and r.deleted = false")
    Optional<RateCard> findActiveById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"category", "site"})
    @Query("""
            select r from RateCard r
            where r.client.id = :clientId and r.deleted = false
            order by r.category.displayOrder asc, r.effectiveFrom desc
            """)
    List<RateCard> findByClientId(@Param("clientId") Long clientId);

    /**
     * Resolves the rate card that applies to a category at a site on a date.
     *
     * <p><strong>This is the query payroll and billing depend on, so the
     * ordering is the contract, not an incidental detail.</strong>
     *
     * <p>It returns candidates ordered by specificity and then recency:
     *
     * <ol>
     *   <li>A row naming {@code siteId} beats a client-wide row (null site) for
     *       the same category. Minimum wages differ by state, so a client with
     *       plants in two states needs per-site rates to be correct.</li>
     *   <li>Among equally specific rows, the latest {@code effectiveFrom} on or
     *       before the date wins - that is what "the rate in force then" means.</li>
     * </ol>
     *
     * <p>A list rather than a single row on purpose: the caller takes the first
     * and can see whether more than one existed. Collapsing that into
     * {@code Optional} inside the query would hide a misconfigured overlap that
     * silently changes someone's wage.
     *
     * <p>Note {@code :siteId} is compared with {@code is null} handling so one
     * query serves both the site-specific and client-wide cases.
     */
    @EntityGraph(attributePaths = {"category", "site"})
    @Query("""
            select r from RateCard r
            where r.client.id = :clientId
              and r.category.id = :categoryId
              and r.deleted = false
              and r.effectiveFrom <= :onDate
              and (r.effectiveTo is null or r.effectiveTo >= :onDate)
              and (r.site is null or (:siteId is not null and r.site.id = :siteId))
            order by case when r.site is null then 1 else 0 end asc,
                     r.effectiveFrom desc
            """)
    List<RateCard> findApplicableOn(@Param("clientId") Long clientId,
                                    @Param("categoryId") Long categoryId,
                                    @Param("siteId") Long siteId,
                                    @Param("onDate") LocalDate onDate);

    /**
     * The open (current) row for a client/category/site combination, if any.
     *
     * <p>Used when superseding a rate: the predecessor has to be closed off the
     * day before the successor starts, or two rows would claim the same date.
     */
    @Query("""
            select r from RateCard r
            where r.client.id = :clientId
              and r.category.id = :categoryId
              and ((:siteId is null and r.site is null) or r.site.id = :siteId)
              and r.deleted = false
              and r.effectiveTo is null
            """)
    List<RateCard> findOpenRows(@Param("clientId") Long clientId,
                                @Param("categoryId") Long categoryId,
                                @Param("siteId") Long siteId);

    /**
     * Rows for the same combination whose date range would overlap a proposed
     * one. Drives the duplicate check when creating a rate card.
     */
    @Query("""
            select r from RateCard r
            where r.client.id = :clientId
              and r.category.id = :categoryId
              and ((:siteId is null and r.site is null) or r.site.id = :siteId)
              and r.deleted = false
              and (:excludeId is null or r.id <> :excludeId)
              and r.effectiveFrom <= coalesce(:proposedTo, r.effectiveFrom)
              and (r.effectiveTo is null or r.effectiveTo >= :proposedFrom)
            """)
    List<RateCard> findOverlapping(@Param("clientId") Long clientId,
                                   @Param("categoryId") Long categoryId,
                                   @Param("siteId") Long siteId,
                                   @Param("proposedFrom") LocalDate proposedFrom,
                                   @Param("proposedTo") LocalDate proposedTo,
                                   @Param("excludeId") Long excludeId);
}
