package com.backend_catcheat.domain.challenge.dto;

public record UnlockRequestDTO(
        Long slotId,
        String imageKey,
        Double lat,
        Double lng,
        String review,
        Integer rating) {}