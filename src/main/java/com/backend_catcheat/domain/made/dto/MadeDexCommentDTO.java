package com.backend_catcheat.domain.made.dto;

import com.backend_catcheat.domain.user.dto.UserBriefDTO;

import java.time.LocalDateTime;

public record MadeDexCommentDTO (
        Long commentId,
        UserBriefDTO author,
        String content,
        int likeCount,
        boolean likedByMe,
        LocalDateTime createdAt
){
}
