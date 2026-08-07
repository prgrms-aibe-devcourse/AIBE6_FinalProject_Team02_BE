package com.backend_catcheat.domain.made.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 사진은 유지할 것의 id와 새로 올린 key를 나눠 받는다.
 * 기존 사진을 key로 되돌려받으면 남의 key를 섞어 보낼 수 있다.
 * 최종 순서는 keepPhotoIds 다음에 newImageKeys다.
 */
public record MadeDexRecordUpdateRequestDTO(
        Long slotId,
        LocalDate loggedOn,
        List<Long> keepPhotoIds,
        List<String> newImageKeys,
        List<String> foodNames,
        String memo,
        String locationName,
        Double lat,
        Double lng
) {}
