package com.hrsolution.deployment.repository;

import com.hrsolution.deployment.entity.Deployment;
import com.hrsolution.deployment.entity.DeploymentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeploymentRepository extends JpaRepository<Deployment, Long>,
        JpaSpecificationExecutor<Deployment> {

    @EntityGraph(attributePaths = {"worker", "client", "site", "category", "requisition"})
    @Query("select d from Deployment d where d.id = :id and d.deleted = false")
    Optional<Deployment> findActiveById(@Param("id") Long id);

    /**
     * A worker's active deployments, for the overlap check.
     *
     * <p>Returns entities rather than a boolean so the service can name the
     * conflicting posting in the error - "already at Bharat Textiles until 31
     * March" is actionable, "worker is unavailable" is not.
     *
     * <p>Deliberately NOT filtered by date in SQL. A null end date means
     * open-ended, and expressing "overlaps, treating null as infinity" in JPQL
     * is error-prone; the set of one worker's active postings is tiny, so the
     * comparison happens in {@code DateUtils.overlaps} where it is tested.
     */
    @EntityGraph(attributePaths = {"client", "site", "category"})
    @Query("""
            select d from Deployment d
            where d.worker.id = :workerId
              and d.deleted = false
              and d.status = com.hrsolution.deployment.entity.DeploymentStatus.ACTIVE
            """)
    List<Deployment> findActiveForWorker(@Param("workerId") Long workerId);

    @EntityGraph(attributePaths = {"client", "site", "category", "requisition"})
    @Query("""
            select d from Deployment d
            where d.worker.id = :workerId and d.deleted = false
            order by d.startDate desc, d.id desc
            """)
    List<Deployment> findHistoryForWorker(@Param("workerId") Long workerId);

    /** How many workers are posted against a requisition, for fulfilment. */
    @Query("""
            select count(d) from Deployment d
            where d.requisition.id = :requisitionId
              and d.deleted = false
              and d.status = com.hrsolution.deployment.entity.DeploymentStatus.ACTIVE
            """)
    long countActiveForRequisition(@Param("requisitionId") Long requisitionId);

    /** Fulfilment counts for several requisitions in one query. */
    @Query("""
            select d.requisition.id as requisitionId, count(d) as total from Deployment d
            where d.requisition.id in :requisitionIds
              and d.deleted = false
              and d.status = com.hrsolution.deployment.entity.DeploymentStatus.ACTIVE
            group by d.requisition.id
            """)
    List<RequisitionDeploymentCount> countActiveForRequisitions(
            @Param("requisitionIds") Collection<Long> requisitionIds);

    long countBySiteIdAndStatusAndDeletedFalse(Long siteId, DeploymentStatus status);

    long countByClientIdAndStatusAndDeletedFalse(Long clientId, DeploymentStatus status);

    /** Everyone posted at a site on a date - the basis of Phase 7's attendance sheet. */
    @EntityGraph(attributePaths = {"worker", "category"})
    @Query("""
            select d from Deployment d
            where d.site.id = :siteId
              and d.deleted = false
              and d.status = com.hrsolution.deployment.entity.DeploymentStatus.ACTIVE
              and d.startDate <= :onDate
              and (d.endDate is null or d.endDate >= :onDate)
            """)
    List<Deployment> findActiveAtSiteOn(@Param("siteId") Long siteId,
                                        @Param("onDate") LocalDate onDate);

    /** Projection for batched requisition fulfilment counts. */
    interface RequisitionDeploymentCount {
        Long getRequisitionId();

        long getTotal();
    }
}
