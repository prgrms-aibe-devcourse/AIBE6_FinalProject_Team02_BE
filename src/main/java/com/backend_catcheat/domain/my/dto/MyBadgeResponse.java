package com.backend_catcheat.domain.my.dto;

import java.time.LocalDateTime;

/**
 * 마이페이지 뱃지 보관함 항목
 * - equipped: 현재 대표 뱃지로 장착되어 있는지
 * - code: 시스템 뱃지 식별자
 * - imageUrl: 챌린지 커스텀 뱃지의 업로드 이미지
 */
public record MyBadgeResponse(
        Long id,
        String name,
        String code,
        String imageUrl,
        String description,
        LocalDateTime acquiredAt,
        boolean equipped
) {}
