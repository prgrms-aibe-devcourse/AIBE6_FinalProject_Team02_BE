package com.backend_catcheat.domain.auth.controller;

import com.backend_catcheat.domain.auth.service.AuthService;
import com.backend_catcheat.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 관련 엔드포인트.
 * 컨트롤러는 요청/응답만 담당하고 실제 로직은 AuthService에 위임한다(AGENTS.md 계층 규칙).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * access token 재발급.
     * refresh token은 httpOnly 쿠키에 있으므로 @CookieValue로 꺼낸다.
     * 새 토큰은 응답 쿠키로 다시 심기므로 body에는 성공 여부만 담는다.
     */
    @PostMapping("/reissue")
    public ApiResponse<Void> reissue(
            @CookieValue(value = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        authService.reissue(refreshToken, response);
        return ApiResponse.ok();
    }

    /** 로그아웃. Redis의 refresh를 지우고 쿠키를 만료시킨다. */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @CookieValue(value = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        authService.logout(refreshToken, response);
        return ApiResponse.ok();
    }
}