package com.backend_catcheat.domain.notification.event;

import com.backend_catcheat.domain.notification.dto.NotificationDTO;
import com.backend_catcheat.domain.notification.entity.NotificationType;
import com.backend_catcheat.domain.notification.service.NotificationService;
import com.backend_catcheat.global.event.CommentCreatedEvent;
import com.backend_catcheat.global.event.MadeDexMemberJoinedEvent;
import com.backend_catcheat.global.event.MadeDexRecordUploadedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class MadeDexNotificationEventListener {

    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    /** AFTER_COMMIT은 원 트랜잭션의 커밋 콜스택 안에서 동기 실행되어, 여기서 새 트랜잭션을 열면
     "No active transaction" 예외가 난다. @Async로 별 스레드에서 돌려 그 콜스택을 벗어난다. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentCreated(CommentCreatedEvent event) {
        NotificationDTO dto = notificationService.create(
                event.recipientId(),
                event.actorId(),
                NotificationType.MADE_DEX_COMMENT_ADDED,
                event.commentId(),
                Map.of("madeDexId", event.madeDexId(), "recordId", event.madeDexRecordId())
        );

        messagingTemplate.convertAndSendToUser(
                event.recipientId().toString(),
                "/queue/notifications",
                dto
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRecordUpload(MadeDexRecordUploadedEvent event) {
        push(
                event.recipientId(),
                event.actorId(),
                NotificationType.FRIEND_CARD_REGISTERED, event.recordId(),
                Map.of("madeDexId", event.madeDexId(), "recordId", event.recordId())
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberJoined(MadeDexMemberJoinedEvent event) {
        push(
                event.recipientId(),
                event.actorId(),
                NotificationType.MADE_DEX_JOINED,
                event.madeDexId(),
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
