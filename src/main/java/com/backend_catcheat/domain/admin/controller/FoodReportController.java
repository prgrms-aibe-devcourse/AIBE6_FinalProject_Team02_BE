package com.backend_catcheat.domain.admin.controller;

import com.backend_catcheat.domain.admin.dto.FoodReportCreateRequestDTO;
import com.backend_catcheat.domain.admin.dto.FoodReportResponseDTO;
import com.backend_catcheat.domain.admin.service.FoodReportService;
import com.backend_catcheat.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
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
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class FoodReportController {

    private final FoodReportService foodReportService;

    @PostMapping
    public ApiResponse<FoodReportResponseDTO> report(@RequestBody FoodReportCreateRequestDTO request) {
        return ApiResponse.ok(foodReportService.createReport(request.name()));
    }
}