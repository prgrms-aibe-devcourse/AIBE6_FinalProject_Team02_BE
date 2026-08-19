package com.backend_catcheat.domain.notification.dto;

import com.backend_catcheat.domain.notification.entity.NotificationType;

import java.time.LocalDateTime;
import java.util.Map;

public record NotificationDTO (
        Long notificationId,
        NotificationType type,
        Long actorId,
        Long targetId,
        boolean read,
        Map<String, Object> payload,
        LocalDateTime createdAt,
        String actorNickname
){

}
