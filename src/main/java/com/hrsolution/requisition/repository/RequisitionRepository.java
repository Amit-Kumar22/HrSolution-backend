package com.hrsolution.requisition.repository;

import com.hrsolution.requisition.entity.Requisition;
import com.hrsolution.requisition.entity.RequisitionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RequisitionRepository extends JpaRepository<Requisition, Long>,
        JpaSpecificationExecutor<Requisition> {

    @EntityGraph(attributePaths = {"client", "site", "category", "raisedBy", "approvedBy"})
    @Query("select r from Requisition r where r.id = :id and r.deleted = false")
    Optional<Requisition> findActiveById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"client", "site", "category", "statusHistory"})
    @Query("select r from Requisition r where r.id = :id and r.deleted = false")
    Optional<Requisition> findActiveWithHistoryById(@Param("id") Long id);

    /**
     * Open requirements at a site, for the deployment screen.
     *
     * <p>"Open" is the committed, non-terminal statuses - the ones a worker can
     * actually be posted against.
     */
    @EntityGraph(attributePaths = {"category"})
    @Query("""
            select r from Requisition r
            where r.site.id = :siteId
              and r.deleted = false
              and r.status in :statuses
            order by r.requiredFrom asc
            """)
    List<Requisition> findBySiteAndStatusIn(@Param("siteId") Long siteId,
                                            @Param("statuses") Collection<RequisitionStatus> statuses);

    long countByClientIdAndStatusAndDeletedFalse(Long clientId, RequisitionStatus status);

    /**
     * Requirements starting on or before a date that are still committed.
     *
     * <p>Drives the "starting soon and not filled" list, which is the one
     * operations screen that prevents a client being let down on a start date.
     */
    @EntityGraph(attributePaths = {"client", "site", "category"})
    @Query("""
            select r from Requisition r
            where r.deleted = false
              and r.requiredFrom <= :cutoff
              and r.status in (
                   com.hrsolution.requisition.entity.RequisitionStatus.APPROVED,
                   com.hrsolution.requisition.entity.RequisitionStatus.IN_PROGRESS,
                   com.hrsolution.requisition.entity.RequisitionStatus.PARTIALLY_FULFILLED)
            order by r.requiredFrom asc
            """)
    List<Requisition> findCommittedStartingBy(@Param("cutoff") LocalDate cutoff);
}
