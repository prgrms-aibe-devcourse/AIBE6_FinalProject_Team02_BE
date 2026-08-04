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
    public record SlotDetail(Long id, String foodName, String placeName, int slotOrder, boolean unlocked, String imageUrl) {}
}