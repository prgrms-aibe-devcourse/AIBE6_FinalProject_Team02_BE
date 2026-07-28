package com.backend_catcheat.domain.my.dto;

import java.time.LocalDateTime;

/**
 * 마이페이지 뱃지 보관함 항목
 * - equipped: 현재 대표 뱃지로 장착되어 있는지
 * - imageUrl: null이면 프론트가 아이콘으로 대체 렌더
 */
public record MyBadgeResponse(
        Long id,
        String name,
        String imageUrl,
        LocalDateTime acquiredAt,
        boolean equipped
) {}
