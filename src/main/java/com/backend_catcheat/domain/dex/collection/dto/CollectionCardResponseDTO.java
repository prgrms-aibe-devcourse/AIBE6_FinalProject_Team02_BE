package com.backend_catcheat.domain.dex.collection.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CollectionCardResponseDTO (
        Long id,
        /** 대표 사진이 0번째, 나머지는 sortOrder 순 */
        List<String> photos,
        String memo,
        String locationName,
        LocalDateTime collectedAt,
        String verificationStatus
){
}
