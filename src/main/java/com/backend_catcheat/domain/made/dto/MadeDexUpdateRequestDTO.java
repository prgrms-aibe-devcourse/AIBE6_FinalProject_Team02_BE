package com.backend_catcheat.domain.made.dto;

import com.backend_catcheat.domain.made.entity.Visibility;

// 부분 수정이 아니라 전체 교체다. null은 "안 바꿈"이 아니라 "비움"으로 읽는다
public record MadeDexUpdateRequestDTO(
        String name,
        String description,
        Visibility visibility,
        String imageKey
) {
}
