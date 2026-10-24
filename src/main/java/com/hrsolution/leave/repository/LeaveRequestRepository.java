package com.hrsolution.leave.repository;

import com.hrsolution.leave.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long>,
        JpaSpecificationExecutor<LeaveRequest> {
    @Query("select lr from LeaveRequest lr where lr.worker.id = :workerId and lr.deleted = false "
            + "and lr.status in ('PENDING','APPROVED') and not (lr.toDate < :from or lr.fromDate > :to)")
    List<LeaveRequest> findOverlapping(@Param("workerId") Long workerId,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);
}
