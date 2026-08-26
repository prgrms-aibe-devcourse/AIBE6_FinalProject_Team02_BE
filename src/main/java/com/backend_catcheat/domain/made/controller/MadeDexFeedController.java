package com.backend_catcheat.domain.made.controller;

import com.backend_catcheat.domain.made.dto.MadeDexFeedDTO;
import com.backend_catcheat.domain.made.service.MadeDexFeedService;
import com.backend_catcheat.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "로그잇 · 식탁(피드)", description = "하루치 기록을 **사람 단위 카드**로 늘어놓은 뷰. 같은 날의 데이터를 끼니 층으로 보는 것이 하루 카드다.")
@RestController
@RequestMapping("/api/v1/made-dexes/{madeDexId}/feed")
@RequiredArgsConstructor
public class MadeDexFeedController {

    private final MadeDexFeedService madeDexFeedService;

    /** 하루치 식탁. date를 안 주면 오늘이다 */
    @Operation(summary = "하루 식탁 조회", description = "`date` 를 안 주면 오늘이다. 멤버별 기록이 카드로 묶여 내려간다.")
    @GetMapping
    public ApiResponse<MadeDexFeedDTO> feed(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(madeDexFeedService.findFeed(userId, madeDexId, date));
    }
}
