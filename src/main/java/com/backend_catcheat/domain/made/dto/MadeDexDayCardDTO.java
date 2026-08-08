package com.backend_catcheat.domain.made.dto;

import java.time.LocalDate;
import java.util.List;

/** 하루 카드
 * 끼니(층)마다 음식 아이템이 놓이고, 하단에 담긴 사람과 통계가 붙는다 */
public record MadeDexDayCardDTO(
        LocalDate date,
        List<MadeDexDayCardSlotDTO> slots,
        List<MadeDexDayCardParticipantDTO> participants,
        MadeDexDayCardStatsDTO stats
) {}
