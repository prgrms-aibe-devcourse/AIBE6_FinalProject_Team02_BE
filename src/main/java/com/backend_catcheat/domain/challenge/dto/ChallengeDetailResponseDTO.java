package com.backend_catcheat.domain.challenge.dto;

import com.backend_catcheat.domain.challenge.entity.PeriodType;

import java.time.LocalDateTime;
import java.util.List;


public record ChallengeDetailResponseDTO(
        Long id,
        String name,
        String description,
        PeriodType periodType,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        Long rewardBadgeId,
        long participantCount,
        boolean joined,       // 내가 참여했는지
        boolean completed,    // 내가 완료했는지
        boolean owner,        // 내가 개설자인지(삭제/종료 노출용)
        String imageUrl,      // 대표 이미지(프리사인 URL)
        List<SlotDetail> slots
) {
    public record SlotDetail(
            Long id, String foodName, String placeName, int slotOrder,
            boolean unlocked,
            String imageUrl,          // 개설자가 등록한 목표 사진(미해금이면 흑백)
            String myImageUrl,        // 내가 인증한 사진(해금 시) — 없으면 null
            LocalDateTime unlockedAt, // 내가 인증한 시각 — 없으면 null
            String storeName,         // 가게명
            String description        // 설명/팁
    ) {}
}