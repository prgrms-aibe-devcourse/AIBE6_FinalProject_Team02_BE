package com.backend_catcheat.domain.admin.dto;

import com.backend_catcheat.domain.admin.entity.RegistrationRequestStatus;

import java.time.LocalDateTime;

public record FoodRegistrationRequestResponseDTO(
        Long id,
        Long registrationId,
        String description,
        String failureReason,
        RegistrationRequestStatus status,
        LocalDateTime createdAt
){
    public static FoodRegistrationRequestResponseDTO from(com.backend_catcheat.domain.admin.entity.FoodRegistrationRequest request) {
        return new FoodRegistrationRequestResponseDTO(
                request.getId(),
                request.getRegistrationId(),
                request.getDescription(),
                request.getFailureReason(),
                request.getStatus(),
                request.getCreatedAt()
        );
    }

}
