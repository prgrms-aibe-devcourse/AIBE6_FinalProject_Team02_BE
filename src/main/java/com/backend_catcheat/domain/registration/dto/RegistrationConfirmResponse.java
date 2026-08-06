package com.backend_catcheat.domain.registration.dto;

import java.util.List;

public record RegistrationConfirmResponse(
        Long registrationId,
        List<UnlockedSlot> unlocked,
        List<PendingSlot> awaitingReview,
        long collectedCount,
        long totalSlots
) {

    public record UnlockedSlot(
            Long slotId,
            String slotName,
            String category,
            Long cardId,
            int rank,
            boolean firstUnlock
    ) {
    }

    public record PendingSlot(
            Long slotId,
            String slotName,
            String category,
            Long cardId
    ) {
    }
}
