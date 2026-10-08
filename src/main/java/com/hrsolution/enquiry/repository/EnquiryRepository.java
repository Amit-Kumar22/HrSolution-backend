package com.hrsolution.enquiry.repository;

import com.hrsolution.enquiry.entity.Enquiry;
import com.hrsolution.enquiry.entity.EnquiryStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface EnquiryRepository extends JpaRepository<Enquiry, Long>,
        JpaSpecificationExecutor<Enquiry> {

    @EntityGraph(attributePaths = {"category", "assignedTo"})
    @Query("select e from Enquiry e where e.id = :id and e.deleted = false")
    Optional<Enquiry> findActiveById(@Param("id") Long id);

    /** Dashboard counter for Phase 10. */
    long countByStatusAndDeletedFalse(EnquiryStatus status);

    /**
     * How many enquiries one IP has submitted recently.
     *
     * <p>A second line of defence behind the per-IP rate limiter: the limiter
     * lives in memory and resets when the process restarts, whereas this counts
     * what is actually in the database.
     */
    @Query("""
            select count(e) from Enquiry e
            where e.ipAddress = :ipAddress and e.createdAt > :since
            """)
    long countRecentFromIp(@Param("ipAddress") String ipAddress, @Param("since") Instant since);
}
