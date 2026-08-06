package com.backend_catcheat.domain.challenge.dto;

import com.backend_catcheat.domain.challenge.entity.ChallengeType;
import com.backend_catcheat.domain.challenge.entity.PeriodType;
import com.backend_catcheat.domain.challenge.entity.VerifyType;

import java.time.LocalDateTime;
import java.util.List;


public record ChallengeDetailResponseDTO(
        Long id,
        String name,
        String description,
        ChallengeType challengeType,
        PeriodType periodType,
        VerifyType verifyType,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        Long rewardBadgeId,
        long participantCount,
        boolean joined,       // 내가 참여했는지
        boolean completed,    // 내가 완료했는지
        List<SlotDetail> slots
) {
    public record SlotDetail(
            Long id, String foodName, String placeName, int slotOrder,
            boolean unlocked,
            String imageUrl,          // 개설자가 등록한 목표 사진(미해금이면 흑백)
            String myImageUrl,        // 내가 인증한 사진(해금 시) — 없으면 null
            LocalDateTime unlockedAt  // 내가 인증한 시각 — 없으면 null
    ) {}
}