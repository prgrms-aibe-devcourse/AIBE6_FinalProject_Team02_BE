package com.backend_catcheat.domain.made.controller;

import com.backend_catcheat.domain.made.dto.MadeDexDayCardCalendarDTO;
import com.backend_catcheat.domain.made.dto.MadeDexDayCardCoverRequestDTO;
import com.backend_catcheat.domain.made.dto.MadeDexDayCardDTO;
import com.backend_catcheat.domain.made.service.MadeDexDayCardService;
import com.backend_catcheat.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/made-dexes/{madeDexId}/day-card")
@RequiredArgsConstructor
public class MadeDexDayCardController {
    private final MadeDexDayCardService madeDexDayCardService;

    /** 오늘의 하루 카드 */
    @GetMapping
    public ApiResponse<MadeDexDayCardDTO> dayCard(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.ok(madeDexDayCardService.findDayCard(userId, madeDexId, date));
    }

    /** 냉장고에 놓일 대표 사진 지정 */
    @PatchMapping("/records/{recordId}/cover")
    public ApiResponse<Void> cover(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId,
            @PathVariable Long recordId,
            @RequestBody MadeDexDayCardCoverRequestDTO request
    ) {
        madeDexDayCardService.changeCover(userId, madeDexId, recordId, request.photoId());
        return ApiResponse.ok();
    }

    /** 캘린더 마커 */
    @GetMapping("/calendar")
    public ApiResponse<MadeDexDayCardCalendarDTO> calendar(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId,
            @RequestParam int year,
            @RequestParam int month
    ) {
        return ApiResponse.ok(madeDexDayCardService.findCalendar(userId, madeDexId, year, month));
    }
}
