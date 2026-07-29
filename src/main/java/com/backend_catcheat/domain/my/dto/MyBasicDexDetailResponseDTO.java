package com.backend_catcheat.domain.my.dto;

import com.backend_catcheat.domain.dex.collection.dto.CollectionCardResponseDTO;

import java.time.LocalDateTime;
import java.util.List;

public record MyBasicDexDetailResponseDTO (
        Long id,
        String name,
        String category,
        String illustrationUrl,
        boolean unlocked,
        int rank,
        LocalDateTime firstCollectedAt,
        List<CollectionCardResponseDTO> cards
){
}
