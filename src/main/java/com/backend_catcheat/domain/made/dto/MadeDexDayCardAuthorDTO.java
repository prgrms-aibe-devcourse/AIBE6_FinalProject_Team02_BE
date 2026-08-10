package com.backend_catcheat.domain.made.dto;

/** 음식 아이템·담긴 사람에 붙는 작성자 표시 */
public record MadeDexDayCardAuthorDTO(
        Long userId,
        String nickname,
        String profileImageUrl,
        boolean me
) {}
