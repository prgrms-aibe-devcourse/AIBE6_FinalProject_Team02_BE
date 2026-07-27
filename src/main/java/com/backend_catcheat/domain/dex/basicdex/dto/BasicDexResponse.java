package com.backend_catcheat.domain.dex.basicdex.dto;

import com.backend_catcheat.domain.dex.basicdex.entity.BasicDexEntity;

public record BasicDexResponse(
        Long id,
        String name,
        String category,
        String illustrationUrl
) {
    public static BasicDexResponse of(BasicDexEntity entity, String illustrationUrl) {
        return new BasicDexResponse(
                entity.getId(),
                entity.getName(),
                entity.getCategory().getDisplayName(),
                illustrationUrl
        );
    }
}
