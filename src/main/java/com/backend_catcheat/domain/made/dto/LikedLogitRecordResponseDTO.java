package com.backend_catcheat.domain.made.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 마이 - 내 활동 - 좋아요한 글(로그잇 탭) 한 줄 */
public record LikedLogitRecordResponseDTO(
        Long recordId,
        Long madeDexId,
        String madeDexName,
        LocalDate loggedOn,
        String slotName,
        Long authorId,
        String authorNickname,
        String authorProfileImageUrl,
        String thumbnailUrl,
        double thumbnailCropX,
        double thumbnailCropY,
        int likeCount,
        LocalDateTime likedAt
) {}
