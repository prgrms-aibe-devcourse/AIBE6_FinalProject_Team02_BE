package com.backend_catcheat.domain.challenge.dto;

/**
 * 챌린지 보상 뱃지 생성 요청
 */
public record RewardBadgeCreateRequestDTO(
        String name,        // 개설자가 지정한 뱃지 이름
        String presetCode,  // 프리셋
        String imageKey     // 유저 제작
) {}
