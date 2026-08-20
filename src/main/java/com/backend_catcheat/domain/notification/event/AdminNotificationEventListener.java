package com.backend_catcheat.domain.notification.event;

import com.backend_catcheat.domain.notification.dto.NotificationDTO;
import com.backend_catcheat.domain.notification.entity.NotificationType;
import com.backend_catcheat.domain.notification.service.NotificationService;
import com.backend_catcheat.global.event.FoodReportApprovedEvent;
import com.backend_catcheat.global.event.FoodReportRejectedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AdminNotificationEventListener {

    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFoodReportApproved(FoodReportApprovedEvent event) {
        String food = event.foodName() == null ? "" : "'" + event.foodName() + "' ";
        Map<String, Object> payload = Map.of("message", "제보한 음식 " + food + "승인됐어요");
        push(event.recipientId(), event.actorId(), NotificationType.FOOD_REPORT_APPROVE, event.reportId(), payload);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFoodReportRejected(FoodReportRejectedEvent event) {
        String food = event.foodName() == null ? "" : "'" + event.foodName() + "' ";
        String reason = (event.reason() == null || event.reason().isBlank()) ? "" : " — " + event.reason();
        Map<String, Object> payload = Map.of("message", "제보한 음식 " + food + "거절됐어요" + reason);
        push(event.recipientId(), event.actorId(), NotificationType.FOOD_REPORT_REJECT, event.reportId(), payload);
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
