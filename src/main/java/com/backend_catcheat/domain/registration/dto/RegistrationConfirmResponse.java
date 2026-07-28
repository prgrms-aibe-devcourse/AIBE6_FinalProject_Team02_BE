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

    /** 관리자가 수락해야 열린다. 카드는 이미 만들어져 있고 "검토 대기" 배지가 붙는다 */
    public record PendingSlot(
            Long slotId,
            String slotName,
            String category,
            Long cardId
    ) {
    }
}
