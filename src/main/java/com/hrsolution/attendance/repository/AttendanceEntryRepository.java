package com.hrsolution.attendance.repository;

import com.hrsolution.attendance.entity.AttendanceEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttendanceEntryRepository extends JpaRepository<AttendanceEntry, Long> {
    List<AttendanceEntry> findBySheetId(Long sheetId);
    void deleteBySheetId(Long sheetId);
}
