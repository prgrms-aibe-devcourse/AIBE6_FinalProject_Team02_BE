package com.backend_catcheat.global.event;

public record FoodReportApprovedEvent(
        Long reportId,
        Long actorId,
        Long recipientId,
        String foodName
) {
}
