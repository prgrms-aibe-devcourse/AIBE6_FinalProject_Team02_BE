package com.backend_catcheat.domain.admin.controller;

import com.backend_catcheat.domain.admin.dto.FoodReportCreateRequestDTO;
import com.backend_catcheat.domain.admin.dto.FoodReportResponseDTO;
import com.backend_catcheat.domain.admin.service.ReportService;
import com.backend_catcheat.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 제보 API. 인증된 사용자면 누구나 도감에 없는 음식을 제보할 수 있다.
 * (관리자 처리 API는 /api/v1/admin/reports
 *  사용자 생성용 /api/v1/reports
 *  )
 */
@Tag(name = "음식 제보", description = """
        도감에 없는 음식을 사용자가 제보한다. 인증된 사용자면 누구나 낼 수 있다.
        제보를 **처리**하는 쪽은 관리자 콘솔(`/api/v1/admin/reports`)이다.
        """)
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class FoodReportController {

    private final ReportService reportService;

    @Operation(summary = "음식 제보하기", description = "도감에 없는 음식 이름을 제보한다. 관리자 검토 큐로 들어간다.")
    @PostMapping
    public ApiResponse<FoodReportResponseDTO> report(
            @AuthenticationPrincipal Long userId,
            @RequestBody FoodReportCreateRequestDTO request) {
        return ApiResponse.ok(reportService.createReport(userId, request.name()));
    }
}
