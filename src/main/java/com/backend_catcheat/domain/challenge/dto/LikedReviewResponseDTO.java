package com.backend_catcheat.domain.challenge.dto;

import com.backend_catcheat.domain.challenge.entity.ReviewType;

import java.time.LocalDateTime;

/** 내가 좋아요한 리뷰 한 건 */
public record LikedReviewResponseDTO(
        Long id,
        ReviewType reviewType,              // FOOD | CHALLENGE
        Long challengeId,
        String challengeName,
        Long slotId,                        // 챌린짓 리뷰면 null
        String foodName,                    // 챌린짓 리뷰면 null
        Long reviewerId,
        String reviewerNickname,
        String reviewerProfileImageUrl,
        String content,
        Integer rating,
        int likeCount,
        LocalDateTime createdAt,
        LocalDateTime likedAt
) {}
