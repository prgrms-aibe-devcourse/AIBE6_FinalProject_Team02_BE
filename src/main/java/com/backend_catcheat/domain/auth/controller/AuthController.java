package com.backend_catcheat.domain.auth.controller;

import com.backend_catcheat.domain.auth.dto.UserResponseDTO;
import com.backend_catcheat.domain.auth.service.AuthService;
import com.backend_catcheat.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 인증 관련 엔드포인트.
 * 컨트롤러는 요청/응답만 담당하고 실제 로직은 AuthService에 위임한다(AGENTS.md 계층 규칙).
 */
@Tag(name = "인증", description = """
        소셜 로그인(Google · Kakao · Naver)으로 서비스 JWT를 발급받는다.
        토큰은 httpOnly 쿠키(`access_token` / `refresh_token`)로 오가며 응답 본문에 담기지 않는다.
        """)
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
    @Operation(summary = "토큰 재발급", description = """
            `refresh_token` 쿠키로 access·refresh 를 함께 새로 발급한다(회전).
            이미 사용된 refresh 가 다시 오면 탈취로 보고 해당 세션을 무효화한다.
            새 토큰은 응답 쿠키로 내려가므로 본문에는 성공 여부만 담긴다.
            """)
    @PostMapping("/reissue")
    public ApiResponse<Void> reissue(
            @CookieValue(value = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        authService.reissue(refreshToken, response);
        return ApiResponse.ok();
    }

    /** 로그아웃. Redis의 refresh를 지우고 쿠키를 만료시킨다. */
    @Operation(summary = "로그아웃", description = "Redis에 저장된 refresh 를 지우고 인증 쿠키를 만료시킨다.")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @CookieValue(value = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        authService.logout(refreshToken, response);
        return ApiResponse.ok();
    }
    //@AuthenticationPrincipal : Authentication 객체에서 현재 인증된 사용자의 정보를 직접 가져올 수 있게 해주는 어노테이션
    /** 내 정보 조회. 로그인 상태 확인용. 인증 안 되면 시큐리티가 401 반환. */
    @Operation(summary = "내 정보 조회", description = "로그인 상태 확인용. 인증되지 않으면 401 이 나간다.")
    @GetMapping("/me")
    public ApiResponse<UserResponseDTO> me(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(authService.getMyInfo(userId));
    }

    /** 심사/제출용 리뷰어 로그인(플래그 on일 때만 동작). */
    @Operation(summary = "리뷰어 로그인 (심사용)", description = """
            소셜 로그인 없이 심사용 계정으로 인증 쿠키를 받는다. **Swagger에서 시험할 때 먼저 호출한다.**
            `TEST_LOGIN_ENABLED=true` 일 때만 동작하고, 꺼져 있으면 존재를 숨기기 위해 404 를 돌려준다.
            시크릿이 설정된 환경에서는 `key` 가 일치해야 한다.
            """)
    @PostMapping("/test-login")
    public ApiResponse<Void> testLogin(
            @RequestParam(value = "key", required = false) String key,
            HttpServletResponse response
    ) {
        authService.testLogin(key, response);
        return ApiResponse.ok();
    }
}
