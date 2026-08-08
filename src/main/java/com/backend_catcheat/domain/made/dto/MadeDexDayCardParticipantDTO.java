package com.backend_catcheat.domain.made.dto;

import java.util.List;

/** 그날 카드에 담은 사람 한 명
 * count는 담은 사진 수, captions는 사진에 붙인 글 */
public record MadeDexDayCardParticipantDTO(
        Long userId,
        String nickname,
        String profileImageUrl,
        boolean me,
        int count,
        List<String> captions
) {}
