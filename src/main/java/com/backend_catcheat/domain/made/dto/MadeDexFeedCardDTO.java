package com.backend_catcheat.domain.made.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 슬롯 하나에서 한 사람이 남긴 것 전부를 카드 한 장으로 접는다.
 * 화면이 사진 한 장과 개수만 그리므로 기록마다 상세를 보내지 않는다.
 * recordCount가 0이면 점선 빈 카드다.
 */
public record MadeDexFeedCardDTO(
        Long userId,
        String nickname,
        String profileImageUrl,
        boolean me,
        int recordCount,
        String thumbnailUrl,
        List<String> foodNames,
        List<Long> recordIds,
        // 대표 사진을 낸 기록의 올린 시각. 빈 카드는 null
        LocalDateTime loggedAt
) {}
