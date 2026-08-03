package com.backend_catcheat.domain.made.dto;

import com.backend_catcheat.domain.made.entity.Visibility;

public record MadeDexCreateRequestDTO(
        String name,
        String description,
        Visibility visibility      // null이면 PRIVATE
) {
}
