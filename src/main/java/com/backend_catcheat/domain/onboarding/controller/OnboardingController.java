package com.backend_catcheat.domain.onboarding.controller;

import com.backend_catcheat.domain.onboarding.dto.GuideSeenResponseDTO;
import com.backend_catcheat.domain.onboarding.service.OnboardingService;
import com.backend_catcheat.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboardingController {
    private final OnboardingService onboardingService;

    /** 본 가이드 키 목록 */
    @GetMapping("/guides")
    public ApiResponse<GuideSeenResponseDTO> getGuides(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(onboardingService.getSeenGuides(userId));
    }

    /** 가이드를 본 것으로 기록 */
    @PostMapping("/guides/{key}")
    public ApiResponse<GuideSeenResponseDTO> markGuideSeen(
            @AuthenticationPrincipal Long userId,
            @PathVariable String key) {
        return ApiResponse.ok(onboardingService.markGuideSeen(userId, key));
    }
}
