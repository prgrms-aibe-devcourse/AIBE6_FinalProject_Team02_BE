package com.backend_catcheat.domain.made.dto;

// 원본 S3 key는 내보내지 않는다. 알면 남의 사진을 자기 기록에 붙일 수 있다
public record MadeDexRecordPhotoDTO(
        Long photoId,
        String url,
        String caption,
        double cropX,
        double cropY
) {}
