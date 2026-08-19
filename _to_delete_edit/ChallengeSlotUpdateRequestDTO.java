package com.backend_catcheat.domain.challenge.dto;

import java.util.List;

/**
 * 챌린지 목표 슬롯 수정 요청(전체 목록 기준).
 * - SlotEdit.id: 기존 슬롯이면 그 id, 새로 추가면 null
 * - 참여자가 있으면 추가/삭제는 막고, 기존 슬롯 내용·순서·이미지 수정만 허용
 */
public record ChallengeSlotUpdateRequestDTO(List<SlotEdit> slots) {
    public record SlotEdit(
            Long id,
            String foodName,
            String placeName,
            Double lat,
            Double lng,
            String imageKey,
            String storeName,
            String description
    ) {}
}
