package com.hrsolution.payroll.repository;

import com.hrsolution.payroll.entity.PayrollItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayrollItemRepository extends JpaRepository<PayrollItem, Long> {
    List<PayrollItem> findByPayrollRunId(Long payrollRunId);
}
