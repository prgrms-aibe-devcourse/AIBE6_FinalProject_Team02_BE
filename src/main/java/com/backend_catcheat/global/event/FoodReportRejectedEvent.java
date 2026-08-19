package com.backend_catcheat.global.event;

public record FoodReportRejectedEvent(
        Long reportId,
        Long actorId,
        Long recipientId
) {
}
