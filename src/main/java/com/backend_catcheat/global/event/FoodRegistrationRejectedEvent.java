package com.backend_catcheat.global.event;

public record FoodRegistrationRejectedEvent(
        Long requestId,
        Long actorId,
        Long recipientId,
        String foodName,
        String reason,
        // 도감 알림 클릭 시 해당 칸(BasicDexEntity)으로 라우팅하기 위한 값. 못 찾으면 null
        Long slotId,
        // 거절이면 칸이 안 열려 상세로 보낼 수 없다 — 대신 이 카테고리 목록으로 보낸다
        String category
) {
}
