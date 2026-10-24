package com.hrsolution.attendance.repository;

import com.hrsolution.attendance.entity.AttendanceSheet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface AttendanceSheetRepository extends JpaRepository<AttendanceSheet, Long>,
        JpaSpecificationExecutor<AttendanceSheet> {
    Optional<AttendanceSheet> findBySiteIdAndMonthAndYear(Long siteId, int month, int year);
}
