package com.backend_catcheat.domain.made.dto;

import java.util.List;

// 피드 카드에 그리는 최소 정보. 상세는 단건 조회로 따로 받는다
public record MadeDexRecordSummaryDTO(
        Long recordId,
        String thumbnailUrl,
        int photoCount,
        List<String> foodNames
) {}
