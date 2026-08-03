package com.backend_catcheat.domain.challenge.dto;

/**
 * 보상 뱃지 표시 정보 — 상세 미리보기·완료 팝업용
 */
public record RewardBadgeDTO(
        Long id,
        String name,
        String code,
        String imageUrl
) {}
