package com.backend_catcheat.domain.challenge.dto;

public record ReviewLikeResponseDTO(
        boolean liked,
        int likeCount
) {}
