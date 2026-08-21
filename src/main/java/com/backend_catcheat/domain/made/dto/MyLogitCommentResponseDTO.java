package com.backend_catcheat.domain.made.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 마이 - 내 활동 - 내가 쓴 글(로그잇 탭) 한 줄 */
public record MyLogitCommentResponseDTO(
        Long commentId,
        Long madeDexId,
        String madeDexName,
        Long recordId,
        LocalDate loggedOn,
        String slotName,
        String content,
        int likeCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
