package com.hrsolution.client.repository;

import com.hrsolution.client.entity.ClientContract;
import com.hrsolution.client.entity.ContractStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ClientContractRepository extends JpaRepository<ClientContract, Long>,
        JpaSpecificationExecutor<ClientContract> {

    @EntityGraph(attributePaths = {"client"})
    @Query("select c from ClientContract c where c.id = :id and c.deleted = false")
    Optional<ClientContract> findActiveById(@Param("id") Long id);

    @Query("""
            select c from ClientContract c
            where c.client.id = :clientId and c.deleted = false
            order by c.startDate desc
            """)
    List<ClientContract> findByClientId(@Param("clientId") Long clientId);

    boolean existsByContractNumberIgnoreCase(String contractNumber);

    /**
     * The contract to bill against for a given date.
     *
     * <p>Returns a list rather than one row so the service can detect the
     * "more than one in force" case explicitly instead of silently taking
     * whichever the database returned first - two active overlapping contracts
     * means two different service charges could apply, which is a question for
     * a human.
     */
    @Query("""
            select c from ClientContract c
            where c.client.id = :clientId
              and c.deleted = false
              and c.status = 'ACTIVE'
              and c.startDate <= :onDate
              and (c.endDate is null or c.endDate >= :onDate)
            order by c.startDate desc
            """)
    List<ClientContract> findInForceOn(@Param("clientId") Long clientId,
                                       @Param("onDate") LocalDate onDate);

    /**
     * Other ACTIVE or DRAFT contracts for the client, for the overlap check.
     * EXPIRED and TERMINATED rows are excluded - a finished contract cannot
     * conflict with a new one.
     */
    @Query("""
            select c from ClientContract c
            where c.client.id = :clientId
              and c.deleted = false
              and c.status in ('ACTIVE', 'DRAFT')
              and (:excludeId is null or c.id <> :excludeId)
            """)
    List<ClientContract> findLiveForOverlapCheck(@Param("clientId") Long clientId,
                                                 @Param("excludeId") Long excludeId);

    /** Drives the expiry reminders in Phase 10. */
    @Query("""
            select c from ClientContract c
            where c.deleted = false and c.status = 'ACTIVE'
              and c.endDate is not null and c.endDate between :from and :to
            order by c.endDate asc
            """)
    List<ClientContract> findActiveExpiringBetween(@Param("from") LocalDate from,
                                                   @Param("to") LocalDate to);

    long countByClientIdAndStatusAndDeletedFalse(Long clientId, ContractStatus status);
}
