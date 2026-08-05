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
        Long rankScore,         // 현재 정렬 지표값(LATEST/FINISHED면 null)
        boolean joined          // 요청 유저의 참여 여부(목록에서 참여중 표시)
){

}
