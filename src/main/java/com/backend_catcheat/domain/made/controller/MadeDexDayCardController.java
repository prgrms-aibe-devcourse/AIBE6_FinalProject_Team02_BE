package com.backend_catcheat.domain.made.controller;

import com.backend_catcheat.domain.made.dto.MadeDexDayCardDTO;
import com.backend_catcheat.domain.made.service.MadeDexDayCardService;
import com.backend_catcheat.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(madeDexDayCardService.findDayCard(userId, madeDexId, date));
    }
}
