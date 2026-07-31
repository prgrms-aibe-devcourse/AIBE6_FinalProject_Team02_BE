package com.backend_catcheat.domain.challenge.dto;

public record UnlockResponseDTO(long unlockedCount, long totalSlots, boolean completed) {}
