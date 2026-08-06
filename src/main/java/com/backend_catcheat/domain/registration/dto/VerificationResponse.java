package com.backend_catcheat.domain.registration.dto;

import java.util.List;

public record VerificationResponse(
        Long registrationId,
        List<SlotVerdict> verdicts,
        boolean allMatched,
        int retriesLeft
) {

    public record SlotVerdict(
            Long slotId,
            String slotName,
            String category,
            boolean matched,
            double confidence,
            String reason
    ) {
    }
}
