package com.hrsolution.worker.repository;

import com.hrsolution.worker.entity.DocumentVerificationStatus;
import com.hrsolution.worker.entity.WorkerDocument;
import com.hrsolution.worker.entity.WorkerDocumentType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WorkerDocumentRepository extends JpaRepository<WorkerDocument, Long>,
        JpaSpecificationExecutor<WorkerDocument> {

    @EntityGraph(attributePaths = {"worker", "document", "verifiedBy"})
    @Query("select d from WorkerDocument d where d.id = :id and d.deleted = false")
    Optional<WorkerDocument> findActiveById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"document", "verifiedBy"})
    @Query("""
            select d from WorkerDocument d
            where d.worker.id = :workerId and d.deleted = false
            order by d.documentType asc, d.id desc
            """)
    List<WorkerDocument> findForWorker(@Param("workerId") Long workerId);

    /**
     * The most recent document of a type for a worker.
     *
     * <p>Used for the deployment gate: a police verification is re-issued
     * periodically, and what matters is the latest one, not whether any exists.
     */
    @EntityGraph(attributePaths = {"document"})
    @Query("""
            select d from WorkerDocument d
            where d.worker.id = :workerId
              and d.documentType = :type
              and d.deleted = false
            order by d.issuedOn desc, d.id desc
            limit 1
            """)
    Optional<WorkerDocument> findLatestOfType(@Param("workerId") Long workerId,
                                              @Param("type") WorkerDocumentType type);

    long countByWorkerIdAndVerificationStatusAndDeletedFalse(
            Long workerId, DocumentVerificationStatus status);

    /**
     * Documents lapsing on or before a date, for the Phase 10 reminders.
     *
     * <p>Only verified ones: chasing the renewal of a document nobody checked
     * in the first place is the wrong conversation.
     */
    @EntityGraph(attributePaths = {"worker"})
    @Query("""
            select d from WorkerDocument d
            where d.deleted = false
              and d.expiresOn is not null
              and d.expiresOn <= :cutoff
              and d.verificationStatus
                  = com.hrsolution.worker.entity.DocumentVerificationStatus.VERIFIED
            order by d.expiresOn asc
            """)
    List<WorkerDocument> findExpiringBy(@Param("cutoff") LocalDate cutoff);
}
