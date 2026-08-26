package com.backend_catcheat.domain.registration.controller;

import com.backend_catcheat.domain.registration.dto.RegistrationConfirmRequest;
import com.backend_catcheat.domain.registration.dto.RegistrationConfirmResponse;
import com.backend_catcheat.domain.registration.dto.VerificationRequest;
import com.backend_catcheat.domain.registration.dto.VerificationResponse;
import com.backend_catcheat.domain.registration.service.FoodVerificationService;
import com.backend_catcheat.domain.registration.service.RegistrationConfirmService;
import com.backend_catcheat.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "등록 · AI 판별", description = """
        사진 한 장으로 음식을 판정하고 도감 칸을 여는 단일 등록 플로우.
        판정(verifications) → 확정(cards) 두 단계로 나뉜다. 사진 없는 등록 경로는 없다.
        """)
@RestController
@RequestMapping("/api/v1/register")
@RequiredArgsConstructor
public class RegistrationController {

    private final FoodVerificationService foodVerificationService;
    private final RegistrationConfirmService registrationConfirmService;

    @Operation(summary = "사진 음식 판별", description = """
            업로드된 사진(S3 key)과 음식 이름들을 받아 AI가 각각 있는지 판정한다.
            응답 형식은 `json_schema` 로 강제하므로 실패는 파싱 오류가 아니라 스키마 위반으로 드러난다.
            사진은 최대 5장, 한 번에 판정할 음식도 최대 5개다.
            """)
    @PostMapping("/verifications")
    public ApiResponse<VerificationResponse> verify(
            @AuthenticationPrincipal Long userId,
            @RequestBody VerificationRequest request) {
        return ApiResponse.ok(foodVerificationService.verify(userId, request));
    }

    @Operation(summary = "등록 확정 · 도감 칸 해금", description = """
            판별 결과를 확정해 수집 카드를 만들고 칸을 연다.
            이미 열린 칸이면 칸은 그대로 두고 별(rank)만 오른다 — 수집률은 칸 기준이라 변하지 않는다.
            """)
    @PostMapping("/registrations/{registrationId}/cards")
    public ApiResponse<RegistrationConfirmResponse> confirm(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long registrationId,
            @RequestBody RegistrationConfirmRequest request) {
        return ApiResponse.ok(registrationConfirmService.confirm(userId, registrationId, request));
    }
}
