package com.backend_catcheat.domain.admin.controller;

import com.backend_catcheat.domain.admin.dto.FoodRegistrationRequestResponseDTO;
import com.backend_catcheat.domain.admin.dto.FoodReportResponseDTO;
import com.backend_catcheat.domain.admin.dto.RejectRequestDTO;
import com.backend_catcheat.domain.admin.entity.ReportStatus;
import com.backend_catcheat.domain.admin.service.RegistrationRequestService;
import com.backend_catcheat.domain.admin.service.ReportService;
import com.backend_catcheat.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 관리자 콘솔 (ADM). 경로가 /api/v1/admin/** 이라 SecurityConfig의 hasRole("ADMIN")이 막는다.
 * 큐별로 서비스가 분리돼 있어, 이 컨트롤러는 요청을 각 서비스로 위임만 한다.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ReportService reportService;
    private final RegistrationRequestService registrationRequestService;

    // ===== 제보 큐 =====

    @GetMapping("/reports")
    public ApiResponse<List<FoodReportResponseDTO>> getReports(
            @RequestParam(defaultValue = "PENDING") ReportStatus status) {
        return ApiResponse.ok(reportService.getReports(status));
    }

    @PatchMapping("/reports/{reportId}/accept")
    public ApiResponse<Void> acceptReport(
            @AuthenticationPrincipal Long adminId,
            @PathVariable Long reportId
    ) {
        reportService.acceptReport(adminId, reportId);
        return ApiResponse.ok();
    }

    @PatchMapping("/reports/{reportId}/reject")
    public ApiResponse<Void> rejectReport(
            @AuthenticationPrincipal Long adminId,
            @PathVariable Long reportId,
            @RequestBody RejectRequestDTO request
    ) {
        reportService.rejectReport(adminId, reportId, request.reason());
        return ApiResponse.ok();
    }

    // ===== 등록 요청 큐 =====

    @GetMapping("/registration-requests")
    public ApiResponse<List<FoodRegistrationRequestResponseDTO>> getPendingRequests() {
        return ApiResponse.ok(registrationRequestService.getPendingRequests());
    }

    @PatchMapping("/registration-requests/{requestId}/complete")
    public ApiResponse<Void> completeRequest(@PathVariable Long requestId) {
        registrationRequestService.completeRequest(requestId);
        return ApiResponse.ok();
    }

    @PatchMapping("/registration-requests/{requestId}/reject")
    public ApiResponse<Void> rejectRequest(
            @PathVariable Long requestId,
            @RequestBody RejectRequestDTO request
    ) {
        registrationRequestService.rejectRequest(requestId, request.reason());
        return ApiResponse.ok();
    }


}
