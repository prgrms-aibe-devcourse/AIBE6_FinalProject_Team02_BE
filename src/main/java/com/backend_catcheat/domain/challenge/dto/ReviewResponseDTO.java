package com.backend_catcheat.domain.challenge.dto;

import com.backend_catcheat.domain.badge.dto.EquippedBadgeViewDTO;

import java.time.LocalDateTime;

public record ReviewResponseDTO(
        Long id,
        Long reviewerId,
        String reviewerNickname,
        String reviewerProfileImageUrl,
        EquippedBadgeViewDTO reviewerEquippedBadge,
        String content,
        Integer rating,
        int likeCount,
        boolean likedByMe,
        boolean mine,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
