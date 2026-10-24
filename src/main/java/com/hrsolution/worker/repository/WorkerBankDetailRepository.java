package com.hrsolution.worker.repository;

import com.hrsolution.worker.entity.WorkerBankDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkerBankDetailRepository extends JpaRepository<WorkerBankDetail, Long> {

    @Query("""
            select b from WorkerBankDetail b
            where b.worker.id = :workerId and b.deleted = false
            order by b.primaryAccount desc, b.id asc
            """)
    List<WorkerBankDetail> findForWorker(@Param("workerId") Long workerId);

    @Query("select b from WorkerBankDetail b where b.id = :id and b.deleted = false")
    Optional<WorkerBankDetail> findActiveById(@Param("id") Long id);

    @Query("""
            select b from WorkerBankDetail b
            where b.worker.id = :workerId and b.primaryAccount = true and b.deleted = false
            """)
    Optional<WorkerBankDetail> findPrimaryForWorker(@Param("workerId") Long workerId);

    /** Duplicate check, on the blind index rather than the ciphertext. */
    @Query("""
            select count(b) > 0 from WorkerBankDetail b
            where b.worker.id = :workerId and b.accountNumberHash = :hash
            """)
    boolean existsForWorkerAndAccountHash(@Param("workerId") Long workerId,
                                          @Param("hash") String hash);

    /**
     * Clears the primary flag across a worker's accounts in one statement.
     *
     * <p>A bulk update rather than a loop because "exactly one primary" has to
     * hold at every moment: demoting the old primary one row at a time leaves a
     * window with none, and a concurrent payroll run reading through that
     * window would find no account to pay into.
     *
     * <p>{@code flushAutomatically} and {@code clearAutomatically} are not
     * optional here. Without the flush, pending changes are written after this
     * statement and undo it; without the clear, entities already loaded keep
     * their stale flag.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update WorkerBankDetail b set b.primaryAccount = false
            where b.worker.id = :workerId and b.primaryAccount = true
            """)
    int clearPrimaryForWorker(@Param("workerId") Long workerId);
}
