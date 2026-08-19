package com.backend_catcheat.domain.notification.event;

import com.backend_catcheat.domain.notification.dto.NotificationDTO;
import com.backend_catcheat.domain.notification.entity.NotificationType;
import com.backend_catcheat.domain.notification.service.NotificationService;
import com.backend_catcheat.global.event.FriendRequestAcceptedEvent;
import com.backend_catcheat.global.event.FriendRequestRejectedEvent;
import com.backend_catcheat.global.event.FriendRequestSentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class FriendNotificationEventListener {

    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    // 요청 알림
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFriendRequestSent(FriendRequestSentEvent event) {
        push(
                event.recipientId(),
                event.actorId(),
                NotificationType.FRIEND_REQUEST_RECEIVED,
                event.requestId(),
                null
        );
    }

    // 수락 알림
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFriendRequestAccepted(FriendRequestAcceptedEvent event) {
        push(
                event.recipientId(),
                event.actorId(),
                NotificationType.FRIEND_REQUEST_ACCEPT,
                event.requestId(),
                null
        );
    }

    // 거절 알림
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFriendRequestRejected(FriendRequestRejectedEvent event) {
        push(
                event.recipientId(),
                event.actorId(),
                NotificationType.FRIEND_REQUEST_REJECT,
                event.requestId(),
                null
        );
    }

    private void push(Long recipientId, Long actorId, NotificationType type, Long targetId, Map<String, Object> payload) {
        NotificationDTO dto = notificationService.create(recipientId, actorId, type, targetId, payload);

        messagingTemplate.convertAndSendToUser(
                recipientId.toString(),
                "/queue/notifications",
                dto
        );

    }
}
