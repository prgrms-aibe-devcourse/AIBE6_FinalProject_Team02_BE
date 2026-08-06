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

    @PostMapping("/verifications")
    public ApiResponse<VerificationResponse> verify(
            @AuthenticationPrincipal Long userId,
            @RequestBody VerificationRequest request) {
        return ApiResponse.ok(foodVerificationService.verify(userId, request));
    }

    @PostMapping("/registrations/{registrationId}/cards")
    public ApiResponse<RegistrationConfirmResponse> confirm(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long registrationId,
            @RequestBody RegistrationConfirmRequest request) {
        return ApiResponse.ok(registrationConfirmService.confirm(userId, registrationId, request));
    }
}
