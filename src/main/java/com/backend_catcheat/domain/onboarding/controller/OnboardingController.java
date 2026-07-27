package com.backend_catcheat.domain.onboarding.controller;

import com.backend_catcheat.domain.onboarding.dto.OnboardingStatusResponse;
import com.backend_catcheat.domain.onboarding.service.OnboardingService;
import com.backend_catcheat.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboardingController {
    private final OnboardingService onboardingService;

    @GetMapping("/status")
    public ApiResponse<OnboardingStatusResponse> getStatus(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(onboardingService.getStatus(userId));
    }

    @PostMapping("/complete")
    public ApiResponse<OnboardingStatusResponse> complete(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(onboardingService.completeOnboarding(userId));
    }
}
