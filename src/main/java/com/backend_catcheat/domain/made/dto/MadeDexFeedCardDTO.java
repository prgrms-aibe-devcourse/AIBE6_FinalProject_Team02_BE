package com.backend_catcheat.domain.made.dto;

import java.util.List;

// records가 비면 화면이 점선 빈 카드를 그린다
public record MadeDexFeedCardDTO(
        Long userId,
        String nickname,
        String profileImageUrl,
        boolean me,
        List<MadeDexRecordSummaryDTO> records
) {}
