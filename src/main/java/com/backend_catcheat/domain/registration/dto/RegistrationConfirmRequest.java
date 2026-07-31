package com.backend_catcheat.domain.registration.dto;

import java.util.List;

public record RegistrationConfirmRequest(
        List<CardInput> cards,
        LocationInput location
) {

    public record CardInput(
            Long slotId,
            List<String> cardPhotoKeys,
            String thumbnailKey,
            String memo
    ) {
    }

    public record LocationInput(String name, Double lat, Double lng) {
    }
}
