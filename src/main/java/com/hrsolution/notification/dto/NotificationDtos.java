package com.hrsolution.notification.dto;

import com.hrsolution.notification.entity.NotificationType;

import java.time.Instant;

public final class NotificationDtos {
    private NotificationDtos() {
    }

    public record Response(
            Long id,
            String title,
            String message,
            NotificationType type,
            String link,
            boolean readFlag,
            Instant readAt,
            Instant createdAt) {
    }

    public record UnreadCountResponse(long count) {
    }
}
