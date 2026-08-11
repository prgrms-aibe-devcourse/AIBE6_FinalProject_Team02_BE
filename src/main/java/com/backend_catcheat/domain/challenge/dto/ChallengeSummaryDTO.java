package com.backend_catcheat.domain.challenge.dto;

import com.backend_catcheat.domain.challenge.entity.PeriodType;

import java.time.LocalDateTime;

public record ChallengeSummaryDTO (
        Long id,
        String name,
        String description,
        PeriodType periodType,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        long participantCount,
        int totalSlots,      // 전체 목표 수 (내 챌린지 진행도용)
        int unlockedCount,   // 내가 해금한 수 (내 챌린지 진행도용)
        Long rankScore,      // 현재 정렬 지표값(LATEST/FINISHED면 null)
        boolean joined,      // 요청 유저의 참여 여부(목록에서 참여중 표시)
        String imageUrl      // 대표 이미지(프리사인 URL)
){

}
