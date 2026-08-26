package com.backend_catcheat.domain.admin.controller;

import com.backend_catcheat.domain.admin.dto.FoodRegistrationRequestResponseDTO;
import com.backend_catcheat.domain.admin.dto.FoodReportResponseDTO;
import com.backend_catcheat.domain.admin.dto.RejectRequestDTO;
import com.backend_catcheat.domain.admin.entity.ReportStatus;
import com.backend_catcheat.domain.admin.service.RegistrationRequestService;
import com.backend_catcheat.domain.admin.service.ReportService;
import com.backend_catcheat.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 관리자 콘솔 (ADM). 경로가 /api/v1/admin/** 이라 SecurityConfig의 hasRole("ADMIN")이 막는다.
 * 큐별로 서비스가 분리돼 있어, 이 컨트롤러는 요청을 각 서비스로 위임만 한다.
 */
@Tag(name = "관리자", description = """
        **ADMIN 권한 전용**(`/api/v1/admin/**` 은 시큐리티가 막는다). 큐가 둘이다.

        - **제보 큐** — 도감에 없는 음식을 사용자가 제보한 것
        - **등록 요청 큐** — AI 판별이 실패한 건. 증빙 사진과 실패 사유가 함께 남아 있어 사람이 다시 본다.
          **수락하면 그때 수집 카드가 칸에 붙는다** — AI 판정이 최종이 아니라는 뜻이다.
        """)
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ReportService reportService;
    private final RegistrationRequestService registrationRequestService;

    // ===== 제보 큐 =====

    @Operation(summary = "제보 목록 조회", description = "`status` 로 대기·수락·반려를 나눠 본다. 기본값은 대기(PENDING).")
    @GetMapping("/reports")
    public ApiResponse<List<FoodReportResponseDTO>> getReports(
            @RequestParam(defaultValue = "PENDING") ReportStatus status) {
        return ApiResponse.ok(reportService.getReports(status));
    }

    @Operation(summary = "제보 수락", description = "제보를 받아들인다. 처리 결과는 제보자에게 알림으로 간다.")
    @PatchMapping("/reports/{reportId}/accept")
    public ApiResponse<Void> acceptReport(
            @AuthenticationPrincipal Long adminId,
            @PathVariable Long reportId
    ) {
        reportService.acceptReport(adminId, reportId);
        return ApiResponse.ok();
    }

    @Operation(summary = "제보 반려", description = "사유를 남겨 반려한다. 사유는 제보자에게 그대로 전달된다.")
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

    @Operation(summary = "등록 요청 대기 목록", description = "AI 판별이 실패해 사람 검토로 넘어온 건. 증빙 사진과 실패 사유가 함께 있다.")
    @GetMapping("/registration-requests")
    public ApiResponse<List<FoodRegistrationRequestResponseDTO>> getPendingRequests() {
        return ApiResponse.ok(registrationRequestService.getPendingRequests());
    }

    @Operation(summary = "등록 요청 수락", description = "**대기 상태로 만들어 둔 수집 카드를 칸에 붙인다.** 이때부터 수집률에 반영된다.")
    @PatchMapping("/registration-requests/{requestId}/complete")
    public ApiResponse<Void> completeRequest(
            @AuthenticationPrincipal Long adminId,
            @PathVariable Long requestId
    ) {
        registrationRequestService.completeRequest(adminId, requestId);
        return ApiResponse.ok();
    }

    @Operation(summary = "등록 요청 반려", description = "사유를 남겨 반려한다. 카드는 칸에 붙지 않는다.")
    @PatchMapping("/registration-requests/{requestId}/reject")
    public ApiResponse<Void> rejectRequest(
            @AuthenticationPrincipal Long adminId,
            @PathVariable Long requestId,
            @RequestBody RejectRequestDTO request
    ) {
        registrationRequestService.rejectRequest(adminId, requestId, request.reason());
        return ApiResponse.ok();
    }


}
