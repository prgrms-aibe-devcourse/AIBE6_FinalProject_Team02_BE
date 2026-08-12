package com.backend_catcheat.domain.made.dto;

import com.backend_catcheat.domain.made.entity.MadeDexRole;

public record MadeDexSummaryDTO(
        Long id,
        String name,
        String description,
        String imageUrl,
        long memberCount,
        MadeDexRole myRole
) {
}
