package com.backend_catcheat.domain.made.dto;

import java.util.List;

/** 그날 카드에 담은 사람 한 명
 * count는 담은 사진 수
 * captions는 사진 순서대로의 글 목록 */
public record MadeDexDayCardParticipantDTO(
        Long userId,
        String nickname,
        String profileImageUrl,
        boolean me,
        int count,
        List<String> captions
) {}
