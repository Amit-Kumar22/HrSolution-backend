package com.hrsolution.notification.service;

import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.notification.dto.NotificationDtos;
import com.hrsolution.notification.mapper.NotificationMapper;
import com.hrsolution.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper mapper;

    @Transactional(readOnly = true)
    public List<NotificationDtos.Response> listUnread(AuthenticatedUser caller) {
        return notificationRepository.findByUserIdAndReadFlagFalseOrderByCreatedAtDesc(caller.id()).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public NotificationDtos.UnreadCountResponse unreadCount(AuthenticatedUser caller) {
        return new NotificationDtos.UnreadCountResponse(notificationRepository.countByUserIdAndReadFlagFalse(caller.id()));
    }

    @Transactional
    public void markRead(Long notificationId, AuthenticatedUser caller) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getUser().getId().equals(caller.id())) {
                n.setReadFlag(true);
                n.setReadAt(Instant.now());
            }
        });
    }

    @Transactional
    public void markAllRead(AuthenticatedUser caller) {
        notificationRepository.markAllRead(caller.id(), Instant.now());
    }
}
