package com.backend_catcheat.domain.notification.event;

import com.backend_catcheat.domain.notification.dto.NotificationDTO;
import com.backend_catcheat.domain.notification.entity.NotificationType;
import com.backend_catcheat.domain.notification.service.NotificationService;
import com.backend_catcheat.global.event.ReviewCreatedEvent;
import com.backend_catcheat.global.event.ReviewLikedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ChallengeNotificationEventListener {

    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    // AFTER_COMMIT은 원 트랜잭션의 커밋 콜스택 안에서 동기 실행되어, 여기서 새 트랜잭션을 열면
    // "No active transaction" 예외가 난다. @Async로 별 스레드에서 돌려 그 콜스택을 벗어난다.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewCreated(ReviewCreatedEvent event) {

        NotificationType type = event.isFoodReview()
                ? NotificationType.CHALLENGE_CARD_REVIEW_ADDED
                : NotificationType.CHALLENGE_REVIEW_ADDED;

        NotificationDTO dto = notificationService.create(
                event.recipientId(),
                event.actorId(),
                type,
                event.reviewId(),
                reviewPayload(event.challengeDexId(), event.slotId())
        );

        messagingTemplate.convertAndSendToUser(
                event.recipientId().toString(),
                "/queue/notifications",
                dto
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewLiked(ReviewLikedEvent event) {
        NotificationDTO dto = notificationService.create(
                event.recipientId(),
                event.actorId(),
                NotificationType.CHALLENGE_REVIEW_LIKED,
                event.reviewId(),
                reviewPayload(event.challengeDexId(), event.slotId())
        );

        messagingTemplate.convertAndSendToUser(
                event.recipientId().toString(),
                "/queue/notifications",
                dto
        );
    }

    // 프론트가 알림 클릭 시 바로 라우팅할 수 있도록 challengeId/slotId를 payload로 실어준다.
    // slotId는 챌린지 식당 리뷰일 때만 값이 있다(챌린지 자체 리뷰는 null이라 Map.of를 못 쓴다).
    private Map<String, Object> reviewPayload(Long challengeDexId, Long slotId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("challengeId", challengeDexId);
        if (slotId != null) payload.put("slotId", slotId);
        return payload;
    }

}
