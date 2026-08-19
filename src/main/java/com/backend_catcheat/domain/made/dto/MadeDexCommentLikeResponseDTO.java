package com.backend_catcheat.domain.made.dto;

public record MadeDexCommentLikeResponseDTO (
        boolean isLike,
        int likeCount
){
}
