package com.backend_catcheat.domain.admin.controller;

import com.backend_catcheat.domain.admin.dto.FoodReportResponseDTO;
import com.backend_catcheat.domain.admin.service.AdminService;
import com.backend_catcheat.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;

    @GetMapping
    public ApiResponse<List<FoodReportResponseDTO>> getPendingReports() {
        return ApiResponse.ok(adminService.getPendingReports());
    }

    @GetMapping("/{reportId}/accept")
    public ApiResponse<Void> acceptReport(@PathVariable Long reportId) {
        adminService.acceptReport(reportId);
        return ApiResponse.ok();
    }
    
    @PatchMapping("/{reportId}/reject")
    public ApiResponse<Void> rejectReport(@PathVariable Long reportId) {
        adminService.rejectReport(reportId);
        return ApiResponse.ok();
    }
}
