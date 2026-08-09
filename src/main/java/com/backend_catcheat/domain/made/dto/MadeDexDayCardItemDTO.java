package com.backend_catcheat.domain.made.dto;

import java.util.List;

/**
 * 카드에 놓이는 아이템 = 기록 하나
 * 사진이 여러 장이어도 선반에는 첫 장만 대표로 놓이고 장수를 숫자로 표시
 * 애니메이션은 photos를 전부 날려야 하므로 나머지 사진도 함께 내려줌
 */
public record MadeDexDayCardItemDTO(
        Long recordId,
        MadeDexDayCardAuthorDTO author,
        List<MadeDexDayCardPhotoDTO> photos
) {}
