package com.backend_catcheat.domain.registration.dto;

import java.util.List;

public record VerificationRequest(
        Long registrationId,
        List<String> photoKeys,
        Integer analysisPhotoIndex,
        List<Long> slotIds
) {
}
