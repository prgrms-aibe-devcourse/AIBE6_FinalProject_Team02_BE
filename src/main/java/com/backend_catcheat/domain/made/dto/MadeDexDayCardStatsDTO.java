package com.backend_catcheat.domain.made.dto;

/** 하루 통계
 * 담긴 음식 사진 수 / 함께 먹은 사람 수 / 기록한 끼니 수 */
public record MadeDexDayCardStatsDTO(
        int foodCount,
        int participantCount,
        int recordedSlotCount
) {}
