package com.backend_catcheat.domain.made.dto;

import java.time.LocalDate;
import java.util.List;

/** 하루 카드  */
public record MadeDexDayCardDTO(
        LocalDate date,
        /** 로그잇 참여자 전원, 가입 순.
         * 카드가 끼니 × 참여자 격자라 그날 기록이 없는 사람도 칸을 차지
         */
        List<MadeDexDayCardAuthorDTO> members,
        List<MadeDexDayCardSlotDTO> slots,
        List<MadeDexDayCardParticipantDTO> participants,
        MadeDexDayCardStatsDTO stats
) {}
