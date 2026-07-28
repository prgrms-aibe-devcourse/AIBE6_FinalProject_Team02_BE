package com.backend_catcheat.domain.admin.controller;

import com.backend_catcheat.domain.admin.dto.ReviewItemResponse;
import com.backend_catcheat.domain.admin.service.ReviewService;
import com.backend_catcheat.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 전용 — 수동 등록 검토 큐.
 *
 * 경로가 /api/v1/admin/** 이라 SecurityConfig의 hasRole("ADMIN")이 걸린다 (§6).
 */
@RestController
@RequestMapping("/api/v1/admin/reviews")
@RequiredArgsConstructor
public class AdminReviewController {

    private final ReviewService reviewService;

    @GetMapping
    public ApiResponse<List<ReviewItemResponse>> pending() {
        return ApiResponse.ok(reviewService.pendingItems());
    }

    /** 수락 — 이 순간 도감 칸이 열린다 */
    @PostMapping("/{reviewItemId}/approval")
    public ApiResponse<Void> approve(@PathVariable Long reviewItemId) {
        reviewService.approve(reviewItemId);
        return ApiResponse.ok();
    }

    /** 반려 — 칸은 끝내 열리지 않는다 */
    @PostMapping("/{reviewItemId}/rejection")
    public ApiResponse<Void> reject(@PathVariable Long reviewItemId) {
        reviewService.reject(reviewItemId);
        return ApiResponse.ok();
    }
}
