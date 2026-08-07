package com.backend_catcheat.domain.made.dto;

import java.time.LocalDate;
import java.util.List;

// 부분 수정이 아니라 전체 교체다. 사진·음식명은 보낸 목록으로 갈아 끼운다
public record MadeDexRecordUpdateRequestDTO(
        Long slotId,
        LocalDate loggedOn,
        List<String> imageKeys,
        List<String> foodNames,
        String memo,
        String locationName,
        Double lat,
        Double lng
) {}
