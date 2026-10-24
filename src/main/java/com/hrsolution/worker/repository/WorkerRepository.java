package com.hrsolution.worker.repository;

import com.hrsolution.worker.entity.Worker;
import com.hrsolution.worker.entity.WorkerStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WorkerRepository extends JpaRepository<Worker, Long>,
        JpaSpecificationExecutor<Worker> {

    @EntityGraph(attributePaths = {"user", "candidate"})
    @Query("select w from Worker w where w.id = :id and w.deleted = false")
    Optional<Worker> findActiveById(@Param("id") Long id);

    /**
     * Finds a worker by the blind index of their Aadhaar.
     *
     * <p>This is the <em>only</em> way to look one up by Aadhaar. The
     * ciphertext uses a random IV, so {@code where aadhaar_encrypted = ?} can
     * never match - see {@code FieldCrypto} for why that trade was made.
     * Callers pass {@code fieldCrypto.blindIndex(aadhaar)}, never the number.
     *
     * <p>Ignores the soft-delete flag to match the unique constraint, which
     * also spans deleted rows. Returning empty for a soft-deleted worker would
     * turn a clear "already on file" message into a constraint violation
     * surfacing as a 500.
     */
    @Query("select w from Worker w where w.aadhaarHash = :hash")
    Optional<Worker> findByAadhaarHashIgnoringSoftDelete(@Param("hash") String hash);

    /** As above, for PAN. */
    @Query("select w from Worker w where w.panHash = :hash")
    Optional<Worker> findByPanHashIgnoringSoftDelete(@Param("hash") String hash);

    @Query("select w from Worker w where w.uan = :uan")
    Optional<Worker> findByUanIgnoringSoftDelete(@Param("uan") String uan);

    @EntityGraph(attributePaths = {"user"})
    @Query("select w from Worker w where w.candidate.id = :candidateId and w.deleted = false")
    Optional<Worker> findActiveByCandidateId(@Param("candidateId") Long candidateId);

    @Query("select w from Worker w where w.user.id = :userId and w.deleted = false")
    Optional<Worker> findActiveByUserId(@Param("userId") Long userId);

    long countByStatusAndDeletedFalse(WorkerStatus status);
}
