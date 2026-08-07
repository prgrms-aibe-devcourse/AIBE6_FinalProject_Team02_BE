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
        List<String> foodNames,
        String memo,
        String locationName,
        Double lat,
        Double lng,
        LocalDateTime createdAt
) {}
