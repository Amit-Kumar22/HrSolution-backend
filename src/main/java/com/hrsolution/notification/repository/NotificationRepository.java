package com.hrsolution.notification.repository;

import com.hrsolution.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {
    List<Notification> findByUserIdAndReadFlagFalseOrderByCreatedAtDesc(Long userId);
    long countByUserIdAndReadFlagFalse(Long userId);

    @Modifying
    @Query("update Notification n set n.readFlag = true, n.readAt = :readAt where n.user.id = :userId and n.readFlag = false")
    int markAllRead(Long userId, Instant readAt);
}
