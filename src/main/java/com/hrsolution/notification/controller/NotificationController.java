package com.hrsolution.notification.controller;

import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.notification.dto.NotificationDtos;
import com.hrsolution.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(ApiPaths.V1 + "/notifications")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Notifications", description = "User notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationDtos.Response> list(@AuthenticationPrincipal AuthenticatedUser caller) {
        return notificationService.listUnread(caller);
    }

    @GetMapping("/unread-count")
    public NotificationDtos.UnreadCountResponse count(@AuthenticationPrincipal AuthenticatedUser caller) {
        return notificationService.unreadCount(caller);
    }

    @PatchMapping("/{id}/read")
    public void markRead(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser caller) {
        notificationService.markRead(id, caller);
    }

    @PostMapping("/read-all")
    public void markAllRead(@AuthenticationPrincipal AuthenticatedUser caller) {
        notificationService.markAllRead(caller);
    }
}
