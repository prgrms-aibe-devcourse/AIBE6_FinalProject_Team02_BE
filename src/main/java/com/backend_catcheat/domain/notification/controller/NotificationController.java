package com.backend_catcheat.domain.notification.controller;

import com.backend_catcheat.domain.notification.dto.NotificationDTO;
import com.backend_catcheat.domain.notification.service.NotificationService;
import com.backend_catcheat.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<List<NotificationDTO>> list(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(notificationService.findByRecipient(userId));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(notificationService.countUnread(userId));
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> markAsRead(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long notificationId
    ) {
        notificationService.markAsRead(userId, notificationId);
        return ApiResponse.ok();
    }

    @PatchMapping("/read-all")
    public ApiResponse<Void> markAllAsRead(@AuthenticationPrincipal Long userId) {
        notificationService.markAllAsRead(userId);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{notificationId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long notificationId
    ) {
        notificationService.delete(userId, notificationId);
        return ApiResponse.ok();
    }

}
