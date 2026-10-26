package com.hrsolution.payroll.repository;

import com.hrsolution.payroll.entity.PayrollRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, Long>, JpaSpecificationExecutor<PayrollRun> {
    Optional<PayrollRun> findByClientIdAndMonthAndYear(Long clientId, int month, int year);
    Optional<PayrollRun> findByMonthAndYear(int month, int year);
}
