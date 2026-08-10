package com.backend_catcheat.domain.made.dto;

import java.util.List;

/** 하루 카드 한 층 = 끼니(시간대)
 * items는 등장 순서(가입 순, 기록 순)로 정렬돼 있다 */
public record MadeDexDayCardSlotDTO(
        Long slotId,
        String name,
        int sortOrder,
        boolean hidden,
        List<MadeDexDayCardItemDTO> items
) {}
