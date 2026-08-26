package com.backend_catcheat.domain.onboarding.controller;

import com.backend_catcheat.domain.onboarding.dto.GuideSeenResponseDTO;
import com.backend_catcheat.domain.onboarding.service.OnboardingService;
import com.backend_catcheat.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "온보딩", description = "화면별 첫 방문 가이드(코치마크)를 봤는지 기록한다. 키 단위라 가이드가 늘어도 스키마는 그대로다.")
@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboardingController {
    private final OnboardingService onboardingService;

    /** 본 가이드 키 목록 */
    @Operation(summary = "본 가이드 목록 조회", description = "이미 확인한 가이드 키 목록. 화면은 여기에 없는 가이드만 띄운다.")
    @GetMapping("/guides")
    public ApiResponse<GuideSeenResponseDTO> getGuides(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(onboardingService.getSeenGuides(userId));
    }

    /** 가이드를 본 것으로 기록 */
    @Operation(summary = "가이드 확인 처리", description = "해당 키의 가이드를 본 것으로 남긴다. 갱신된 전체 목록을 돌려준다.")
    @PostMapping("/guides/{key}")
    public ApiResponse<GuideSeenResponseDTO> markGuideSeen(
            @AuthenticationPrincipal Long userId,
            @PathVariable String key) {
        return ApiResponse.ok(onboardingService.markGuideSeen(userId, key));
    }
}
