package com.backend_catcheat.domain.user.dto;

import com.backend_catcheat.domain.badge.dto.EquippedBadgeViewDTO;

/** 공용 유저 요약(프로필·검색·친구목록) */
public record UserBriefDTO(
        Long userId,
        String nickname,
        String profileImageUrl,
        EquippedBadgeViewDTO equippedBadge
) {}
