package com.backend_catcheat.domain.challenge.dto;

public record ReviewWriteRequestDTO(
        String content,
        Integer rating
) {}
