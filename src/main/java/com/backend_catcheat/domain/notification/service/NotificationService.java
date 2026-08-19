package com.backend_catcheat.domain.notification.service;

import com.backend_catcheat.domain.auth.entity.User;
import com.backend_catcheat.domain.auth.repository.UserRepository;
import com.backend_catcheat.domain.notification.dto.NotificationDTO;
import com.backend_catcheat.domain.notification.entity.Notification;
import com.backend_catcheat.domain.notification.entity.NotificationType;
import com.backend_catcheat.domain.notification.repository.NotificationRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public NotificationDTO create(Long recipientId, Long actorId, NotificationType type, Long targetId, Map<String, Object> payload) {

        String payloadJson = payload == null ? null : toJson(payload);

        Notification notification = Notification.create(recipientId, actorId, type, targetId, payloadJson);

        notificationRepository.save(notification);

        String actorNickname = userRepository.findById(actorId).map(User::getNickname).orElse(null);

        return toDTO(notification, actorNickname);

    }

    public List<NotificationDTO> findByRecipient(Long recipientId) {
        List<Notification> notifications = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId);

        List<Long> actorIds = notifications.stream().map(Notification::getActorId).distinct().toList();
        Map<Long, String> nicknameByActorId = userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));

        return notifications.stream()
                .map(n -> toDTO(n, nicknameByActorId.get(n.getActorId())))
                .toList();
    }

    public long countUnread(Long recipientId) {
        return notificationRepository.countByRecipientIdAndReadAtIsNull(recipientId);
    }



    // 읽음 처리
    @Transactional
    public void markAsRead(Long userId, Long notificationId) {

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if(!notification.getRecipientId().equals(userId)) {
            throw new CustomException(ErrorCode.NOTIFICATION_NOT_RECIPIENT);
        }

        notification.markAsRead(LocalDateTime.now());

    }

    // 알림함 진입 시 한 번에 전부 읽음 처리
    @Transactional
    public void markAllAsRead(Long recipientId) {
        notificationRepository.markAllAsRead(recipientId, LocalDateTime.now());
    }

    private NotificationDTO toDTO(Notification notification, String actorNickname) {
        return new NotificationDTO(
                notification.getId(),
                notification.getType(),
                notification.getActorId(),
                notification.getTargetId(),
                notification.isRead(),
                fromJson(notification.getPayload()),
                notification.getCreatedAt(),
                actorNickname
        );
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("payload 직렬화 실패", e);
        }
    }

    private Map<String, Object> fromJson(String payloadJson) {
        if(payloadJson == null) return null;

        try {
            return objectMapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() {});
        } catch (JacksonException e) {
            throw new IllegalArgumentException("payload 역직렬화 실패", e);
        }
    }
}
