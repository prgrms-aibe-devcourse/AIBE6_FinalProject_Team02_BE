package com.backend_catcheat.domain.notification.controller;

import com.backend_catcheat.domain.notification.dto.NotificationDTO;
import com.backend_catcheat.domain.notification.service.NotificationService;
import com.backend_catcheat.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "알림", description = """
        댓글·좋아요·기록 업로드·친구 요청·관리자 검토 결과가 알림으로 온다.

        각 도메인은 알림 서비스를 직접 부르지 않고 **이벤트만 발행**한다 — 로그잇은 알림을 모른다.
        알림 처리는 **핵심 트랜잭션이 커밋된 뒤**에 돌기 때문에, 알림이 실패해도 댓글이나 기록은 그대로 남는다.
        저장과 동시에 WebSocket(`/queue/notifications`)으로 밀어 주므로, 이 API 는 목록·읽음 처리용이다.
        """)
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "알림 목록 조회", description = "내가 받은 알림을 최신순으로 돌려준다.")
    @GetMapping
    public ApiResponse<List<NotificationDTO>> list(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(notificationService.findByRecipient(userId));
    }

    @Operation(summary = "안 읽은 알림 수", description = "하단 탭의 빨간 점·숫자 배지에 쓴다.")
    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(notificationService.countUnread(userId));
    }

    @Operation(summary = "알림 읽음 처리", description = "알림 하나를 읽은 것으로 남긴다.")
    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> markAsRead(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long notificationId
    ) {
        notificationService.markAsRead(userId, notificationId);
        return ApiResponse.ok();
    }

    @Operation(summary = "알림 전체 읽음 처리", description = "안 읽은 알림을 한 번에 읽음으로 바꾼다.")
    @PatchMapping("/read-all")
    public ApiResponse<Void> markAllAsRead(@AuthenticationPrincipal Long userId) {
        notificationService.markAllAsRead(userId);
        return ApiResponse.ok();
    }

    @Operation(summary = "알림 삭제", description = "내 알림만 지울 수 있다.")
    @DeleteMapping("/{notificationId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long notificationId
    ) {
        notificationService.delete(userId, notificationId);
        return ApiResponse.ok();
    }

}
