package com.backend_catcheat.domain.made.controller;

import com.backend_catcheat.domain.made.dto.MadeDexDayCardCalendarDTO;
import com.backend_catcheat.domain.made.dto.MadeDexDayCardCoverRequestDTO;
import com.backend_catcheat.domain.made.dto.MadeDexDayCardDTO;
import com.backend_catcheat.domain.made.service.MadeDexDayCardService;
import com.backend_catcheat.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "로그잇 · 하루 카드", description = """
        같은 하루 데이터를 식탁(피드)의 사람 단위 카드가 아니라 **끼니 층 + 음식 사진**으로 보여 주는 뷰.
        공유용 9:16 카드 영상은 서버가 아니라 브라우저에서 만든다 — 인코딩은 CPU 를 오래 잡아 API 서버에 올리면 다른 요청을 막는다.
        """)
@RestController
@RequestMapping("/api/v1/made-dexes/{madeDexId}/day-card")
@RequiredArgsConstructor
public class MadeDexDayCardController {
    private final MadeDexDayCardService madeDexDayCardService;

    /** 오늘의 하루 카드 */
    @Operation(summary = "하루 카드 조회", description = "`date` 를 안 주면 오늘이다. 끼니 층별로 사진과 작성자가 묶여 내려간다.")
    @GetMapping
    public ApiResponse<MadeDexDayCardDTO> dayCard(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.ok(madeDexDayCardService.findDayCard(userId, madeDexId, date));
    }

    /** 냉장고에 놓일 대표 사진 지정 */
    @Operation(summary = "대표 사진 지정", description = "내 기록에서 냉장고 뷰에 놓일 대표 사진을 고른다.")
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
    @Operation(summary = "캘린더 마커 조회", description = "해당 연·월에서 기록이 있는 날짜를 표시하기 위한 마커 목록.")
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
