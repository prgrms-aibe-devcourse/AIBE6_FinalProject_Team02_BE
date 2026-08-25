package com.backend_catcheat.global.event;

public record FoodRegistrationApprovedEvent(
        Long requestId,
        Long actorId,
        Long recipientId,
        String foodName,
        // 도감 알림 클릭 시 해당 칸(BasicDexEntity)으로 라우팅하기 위한 값. 못 찾으면 null
        Long slotId
) {
}
