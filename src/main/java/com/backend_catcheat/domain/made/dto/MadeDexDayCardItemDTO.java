package com.backend_catcheat.domain.made.dto;

/**
 * 카드에 놓이는 음식 사진 한 장
 * caption은 사진에 붙인 글로, 없을 수 있다.
 */
public record MadeDexDayCardItemDTO(
        String caption,
        String imageUrl,
        MadeDexDayCardAuthorDTO author
) {}
