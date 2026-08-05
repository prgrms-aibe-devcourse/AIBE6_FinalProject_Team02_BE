package com.backend_catcheat.domain.challenge.dto;

import com.backend_catcheat.domain.challenge.entity.ChallengeType;
import com.backend_catcheat.domain.challenge.entity.PeriodType;

import java.time.LocalDateTime;

public record ChallengeSummaryDTO (
        Long id,
        String name,
        String description,
        ChallengeType challengeType,
        PeriodType periodType,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        long participantCount,
        int totalSlots,      // 전체 목표 수 (내 챌린지 진행도용)
        int unlockedCount    // 내가 해금한 수 (내 챌린지 진행도용)
){

}
