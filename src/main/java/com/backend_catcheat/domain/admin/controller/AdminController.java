package com.backend_catcheat.domain.admin.controller;

import com.backend_catcheat.domain.admin.dto.FoodRegistrationRequestResponseDTO;
import com.backend_catcheat.domain.admin.dto.FoodReportResponseDTO;
import com.backend_catcheat.domain.admin.dto.RejectRequestDTO;
import com.backend_catcheat.domain.admin.service.AdminService;
import com.backend_catcheat.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;

    @GetMapping("/reports")
    public ApiResponse<List<FoodReportResponseDTO>> getPendingReports() {
        return ApiResponse.ok(adminService.getPendingReports());
    }

    @PatchMapping("/reports/{reportId}/accept")
    public ApiResponse<Void> acceptReport(@PathVariable Long reportId) {
        adminService.acceptReport(reportId);
        return ApiResponse.ok();
    }

    @PatchMapping("/reports/{reportId}/reject")
    public ApiResponse<Void> rejectReport(
            @PathVariable Long reportId,
            @RequestBody RejectRequestDTO request
    ) {
        adminService.rejectReport(reportId, request.reason());
        return ApiResponse.ok();
    }
    @GetMapping("/registration-requests")
    public ApiResponse<List<FoodRegistrationRequestResponseDTO>> getPendingRequests() {
        return ApiResponse.ok(adminService.getPendingRequests());
    }


    @PatchMapping("/registration-requests/{requestId}/complete")
    public ApiResponse<Void> completeRequest(@PathVariable Long requestId) {
        adminService.completeRequest(requestId);
        return ApiResponse.ok();
    }


    @PatchMapping("/registration-requests/{requestId}/reject")
    public ApiResponse<Void> rejectRequest(
            @PathVariable Long requestId,
            @RequestBody RejectRequestDTO request
    ) {
        adminService.rejectRequest(requestId, request.reason());
        return ApiResponse.ok();
    }
}
