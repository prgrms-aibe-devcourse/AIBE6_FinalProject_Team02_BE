package com.backend_catcheat.domain.notification.event;

import com.backend_catcheat.domain.auth.entity.Role;
import com.backend_catcheat.domain.auth.repository.UserRepository;
import com.backend_catcheat.domain.notification.dto.NotificationDTO;
import com.backend_catcheat.domain.notification.entity.NotificationType;
import com.backend_catcheat.domain.notification.service.NotificationService;
import com.backend_catcheat.global.event.AdminRegistrationRequestEvent;
import com.backend_catcheat.global.event.AdminReportRequestEvent;
import com.backend_catcheat.global.event.FoodRegistrationApprovedEvent;
import com.backend_catcheat.global.event.FoodRegistrationRejectedEvent;
import com.backend_catcheat.global.event.FoodReportApprovedEvent;
import com.backend_catcheat.global.event.FoodReportRejectedEvent;
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
public class AdminNotificationEventListener {

    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

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

    // 관리자가 등록 요청을 승인 — 검토 대기 카드가 칸에 붙어 해금됨을 요청자에게 알린다
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFoodRegistrationApproved(FoodRegistrationApprovedEvent event) {
        String food = event.foodName() == null ? "" : "'" + event.foodName() + "' ";
        Map<String, Object> payload = messageWithSlot("등록 요청한 음식 " + food + "승인됐어요", event.slotId());
        push(event.recipientId(), event.actorId(), NotificationType.FOOD_REGISTRATION_APPROVE, event.requestId(), payload);
    }

    // 관리자가 등록 요청을 거절 — 사유를 포함해 요청자에게 알린다.
    // 거절이면 칸이 안 열려 상세로 보낼 수 없어 category를 실어 목록으로 보내게 한다
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFoodRegistrationRejected(FoodRegistrationRejectedEvent event) {
        String food = event.foodName() == null ? "" : "'" + event.foodName() + "' ";
        String reason = (event.reason() == null || event.reason().isBlank()) ? "" : " — " + event.reason();
        Map<String, Object> payload = messageWithSlot(
                "등록 요청한 음식 " + food + "거절됐어요" + reason, event.slotId(), event.category());
        push(event.recipientId(), event.actorId(), NotificationType.FOOD_REGISTRATION_REJECT, event.requestId(), payload);
    }

    // Map.of는 값이 null이면 바로 예외를 던져서, slotId·category가 없을 수 있는(카드가 지워진 등) 경우엔 못 쓴다
    private Map<String, Object> messageWithSlot(String message, Long slotId) {
        return messageWithSlot(message, slotId, null);
    }

    private Map<String, Object> messageWithSlot(String message, Long slotId, String category) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", message);
        if (slotId != null) payload.put("slotId", slotId);
        if (category != null) payload.put("category", category);
        return payload;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAdminRegistrationRequest(AdminRegistrationRequestEvent event) {
        String food = event.foodName() == null ? "" :"'" + event.foodName() + "'";
        Map<String, Object> payload = Map.of("message", food + " 음식에 대한 등록 요청이 왔어요");
        userRepository.findAllByRole(Role.ADMIN)
                .stream()
                .filter(admin -> !admin.getId().equals(event.requesterId()))
                .forEach(admin -> push(
                        admin.getId(),
                        event.requesterId(),
                        NotificationType.FOOD_REGISTRATION_REQUEST_RECEIVED,
                        event.requestId(),
                        payload
                        )
                );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAdminReportRequest(AdminReportRequestEvent event) {
        String food = event.foodName() == null ? "" : "'" + event.foodName() + "'";
        Map<String, Object> payload = Map.of("message", food + " 음식에 대한 제보 요청이 왔어요");
        userRepository.findAllByRole(Role.ADMIN)
                .stream()
                .filter(admin -> !admin.getId().equals(event.reporterId()))
                .forEach(admin -> push(
                        admin.getId(),
                        event.reporterId(),
                        NotificationType.FOOD_REPORT_RECEIVED,
                        event.reportId(),
                        payload
                ));
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
