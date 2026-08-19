package com.backend_catcheat.domain.made.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record MadeDexRecordDetailDTO(
        Long recordId,
        Long slotId,
        String slotName,
        LocalDate loggedOn,
        Long authorId,
        String authorNickname,
        boolean mine,
        List<MadeDexRecordPhotoDTO> photos,
        // 올린 시각(Asia/Seoul). created_at은 서버 시간대를 타서 화면에 쓰지 않는다
        LocalDateTime loggedAt,
        int likeCount,
        boolean likedByMe
) {}
