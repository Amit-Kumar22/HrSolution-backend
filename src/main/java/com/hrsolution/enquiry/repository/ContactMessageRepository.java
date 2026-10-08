package com.hrsolution.enquiry.repository;

import com.hrsolution.enquiry.entity.ContactMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long>,
        JpaSpecificationExecutor<ContactMessage> {

    @Query("select m from ContactMessage m where m.id = :id and m.deleted = false")
    Optional<ContactMessage> findActiveById(@Param("id") Long id);

    /** Unread count for the admin notification badge. */
    long countByReadFalseAndSpamFalseAndDeletedFalse();

    @Query("""
            select count(m) from ContactMessage m
            where m.ipAddress = :ipAddress and m.createdAt > :since
            """)
    long countRecentFromIp(@Param("ipAddress") String ipAddress, @Param("since") Instant since);
}
