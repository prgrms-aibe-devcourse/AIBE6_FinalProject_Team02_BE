package com.backend_catcheat.domain.made.dto;

public record MadeDexSlotDTO(
        Long slotId,
        String name,
        int sortOrder,
        boolean hidden,
        boolean hasRecords
) {}
