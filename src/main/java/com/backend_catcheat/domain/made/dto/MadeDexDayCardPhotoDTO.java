package com.backend_catcheat.domain.made.dto;

/**
 * 아이템에 담긴 사진 한 장
 * caption은 사진에 붙인 글, photoId는 대표 사진
 */
public record MadeDexDayCardPhotoDTO(
        Long photoId,
        String caption,
        String imageUrl
) {}
