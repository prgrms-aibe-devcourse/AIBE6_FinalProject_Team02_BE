package com.backend_catcheat.domain.my.dto;

import java.time.LocalDateTime;

public record MyBasicDexResponseDTO (
        Long id,
        String name,
        String category,
        String illustrationUrl,
        boolean unlocked,
        int rank,
        LocalDateTime firstCollectedAt,
        long cardCount
){
}
