package com.backend_catcheat.domain.challenge.dto;

import com.backend_catcheat.domain.challenge.entity.ChallengeType;
import com.backend_catcheat.domain.challenge.entity.PeriodType;
import com.backend_catcheat.domain.challenge.entity.VerifyType;

import java.time.LocalDateTime;
import java.util.List;

public record ChallengeCreateRequestDTO(
        String name,
        String description,
        ChallengeType challengeType,
        PeriodType periodType,
        VerifyType verifyType,
        LocalDateTime startsAt,      // null이면 지금부터
        LocalDateTime endsAt,        // LIMITED면 필수
        Long rewardBadgeId,          // 완료 보상 뱃지(선택 — 김락현 뱃지 연결)
        List<SlotInput> slots        // 목표 음식(최소 5개)
) {
    public record SlotInput(String foodName, String placeName, Double lat, Double lng, String imageKey) {}
}