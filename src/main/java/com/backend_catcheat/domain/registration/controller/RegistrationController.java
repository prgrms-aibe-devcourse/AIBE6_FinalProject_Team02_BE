package com.backend_catcheat.domain.registration.controller;

import com.backend_catcheat.domain.registration.dto.RegistrationConfirmRequest;
import com.backend_catcheat.domain.registration.dto.RegistrationConfirmResponse;
import com.backend_catcheat.domain.registration.dto.VerificationRequest;
import com.backend_catcheat.domain.registration.dto.VerificationResponse;
import com.backend_catcheat.domain.registration.service.FoodVerificationService;
import com.backend_catcheat.domain.registration.service.RegistrationConfirmService;
import com.backend_catcheat.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/register")
@RequiredArgsConstructor
public class RegistrationController {

    private final FoodVerificationService foodVerificationService;
    private final RegistrationConfirmService registrationConfirmService;

    /**
     * POST /api/v1/register/verifications
     *
     * 사진과 유저가 고른 도감 칸이 맞는지 판정한다. 재시도는 같은 registrationId로 다시 부르며,
     * 상한(2회)은 서버가 센다.
     *
     * 유저는 인증 컨텍스트에서 꺼낸다 — 요청 바디의 userId를 신뢰하지 않는다.
     */
    @PostMapping("/verifications")
    public ApiResponse<VerificationResponse> verify(
            @AuthenticationPrincipal Long userId,
            @RequestBody VerificationRequest request) {
        return ApiResponse.ok(foodVerificationService.verify(userId, request));
    }

    /**
     * POST /api/v1/register/registrations/{registrationId}/cards
     *
     * 음식별 기록을 마치고 도감을 연다. 검증을 통과한 칸만 해금되며,
     * 이미 열린 칸이면 카드만 추가되고 별 랭크가 오른다.
     */
    @PostMapping("/registrations/{registrationId}/cards")
    public ApiResponse<RegistrationConfirmResponse> confirm(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long registrationId,
            @RequestBody RegistrationConfirmRequest request) {
        return ApiResponse.ok(registrationConfirmService.confirm(userId, registrationId, request));
    }
}
