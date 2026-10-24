package com.hrsolution.worker.repository;

import com.hrsolution.worker.entity.SalaryStructure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SalaryStructureRepository extends JpaRepository<SalaryStructure, Long> {

    @Query("select s from SalaryStructure s where s.id = :id and s.deleted = false")
    Optional<SalaryStructure> findActiveById(@Param("id") Long id);

    @Query("""
            select s from SalaryStructure s
            where s.worker.id = :workerId and s.deleted = false
            order by s.effectiveFrom desc, s.id desc
            """)
    List<SalaryStructure> findHistoryForWorker(@Param("workerId") Long workerId);

    /** The open-ended structure, i.e. the one in force until superseded. */
    @Query("""
            select s from SalaryStructure s
            where s.worker.id = :workerId and s.effectiveTo is null and s.deleted = false
            """)
    Optional<SalaryStructure> findCurrentForWorker(@Param("workerId") Long workerId);

    /**
     * The structure in force on a date.
     *
     * <p>What Phase 8 calls when recomputing an earlier month: the answer has
     * to be the structure that applied <em>then</em>, not the current one.
     */
    @Query("""
            select s from SalaryStructure s
            where s.worker.id = :workerId
              and s.deleted = false
              and s.effectiveFrom <= :onDate
              and (s.effectiveTo is null or s.effectiveTo >= :onDate)
            order by s.effectiveFrom desc
            limit 1
            """)
    Optional<SalaryStructure> findEffectiveOn(@Param("workerId") Long workerId,
                                              @Param("onDate") LocalDate onDate);
}
