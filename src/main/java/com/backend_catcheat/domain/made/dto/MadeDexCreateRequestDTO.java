package com.backend_catcheat.domain.made.dto;

public record MadeDexCreateRequestDTO(
        String name,
        String description,
        String imageKey            // 업로드된 S3 key. null이면 표지 없음
) {
}
