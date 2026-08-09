package com.backend_catcheat.domain.challenge.dto;

import java.time.LocalDateTime;

public record ReviewResponseDTO(
        Long id,
        Long reviewerId,
        String reviewerNickname,
        String content,
        Integer rating,
        int likeCount,
        boolean likedByMe,
        boolean mine,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
