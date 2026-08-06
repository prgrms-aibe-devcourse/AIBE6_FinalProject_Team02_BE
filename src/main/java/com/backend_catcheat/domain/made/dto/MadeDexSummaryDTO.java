package com.backend_catcheat.domain.made.dto;

import com.backend_catcheat.domain.made.entity.MadeDexRole;
import com.backend_catcheat.domain.made.entity.Visibility;

public record MadeDexSummaryDTO(
        Long id,
        String name,
        String description,
        Visibility visibility,
        String imageUrl,
        long memberCount,
        MadeDexRole myRole
) {
}
