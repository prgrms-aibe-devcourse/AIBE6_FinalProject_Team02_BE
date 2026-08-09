package com.backend_catcheat.domain.made.dto;

import java.util.List;

/** 캘린더 마커 */
public record MadeDexDayCardCalendarDTO(
        int year,
        int month,
        List<Integer> daysWithRecords
) {}
