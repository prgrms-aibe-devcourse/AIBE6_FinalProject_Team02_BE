package com.backend_catcheat.domain.auth.service;

import com.backend_catcheat.domain.auth.dto.UserResponseDTO;
import com.backend_catcheat.domain.auth.entity.Provider;
import com.backend_catcheat.domain.auth.entity.Role;
import com.backend_catcheat.domain.auth.entity.User;
import com.backend_catcheat.domain.auth.repository.UserRepository;
import com.backend_catcheat.domain.auth.token.RefreshTokenStore;
import com.backend_catcheat.domain.onboarding.service.OnboardingService;
import com.backend_catcheat.global.jwt.JwtTokenProvider;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/**
 * 토큰 재발급과 로그아웃을 담당한다.
 * - 재발급: refresh token을 검증하고 Redis 저장값과 대조한 뒤 새 토큰을 발급(회전).
 * - 로그아웃: Redis의 refresh token을 지우고 쿠키를 만료시킨다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";
    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final UserRepository userRepository;
    private final OnboardingService onboardingService;
    private final MeterRegistry meterRegistry;

    /** 로그인 때 구운 쿠키와 같은 값이어야 한다. 다르면 재발급이 Secure를 벗겨 덮어쓴다 */
    @Value("${app.auth.cookie-secure}")
    private boolean cookieSecure;



    @Value("${app.auth.test-login.enabled:false}")
    private boolean testLoginEnabled;

    @Value("${app.auth.test-login.secret:}")
    private String testLoginSecret;

    private static final Provider REVIEWER_PROVIDER = Provider.GOOGLE;
    private static final String REVIEWER_PROVIDER_ID = "reviewer-demo";

        /** 로그인 때 구운 쿠키와 같은 값이어야 한다. differently면 재발급이 Secure를 벗겨 덮어쓴다 */

    public void reissue(String refreshToken, HttpServletResponse response) {
        // 1) refresh token 자체가 유효한지 + 정말 refresh 타입인지 확인
        //    (access token으로 재발급 시도하는 것을 막는다)
        if (refreshToken == null
                || !jwtTokenProvider.validate(refreshToken)
                || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw unauthorized();
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);

        // 2) 새 refresh 준비 후, "저장값이 지금 이 refresh와 같을 때만" 원자적으로 회전(CAS).
        //    동시 재발급이 들어와도 승자는 단 하나 → Redis와 쿠키가 어긋나지 않는다.
        //    실패면: 이미 회전됨(동시성 패자) / 저장값 불일치(탈취 의심) / 만료·로그아웃 → 거부.

        String sessionId = jwtTokenProvider.getSessionId(refreshToken);
        if (sessionId == null) {            // 구버전(sid 없는) 토큰 → 재로그인 유도
            throw unauthorized();
        }
        String newRefresh = jwtTokenProvider.createRefreshToken(userId, sessionId); // 같은 sid 유지
        int rotateResult = refreshTokenStore.rotate(userId, sessionId, refreshToken, newRefresh);

        if (rotateResult == 1) {
            // 정상 승자: access + refresh(new) 모두 갱신
            User user = userRepository.findById(userId).orElseThrow(this::unauthorized);
            String newAccess = jwtTokenProvider.createAccessToken(userId, user.getRole());

            addCookie(response, ACCESS_TOKEN_COOKIE, newAccess, jwtTokenProvider.getAccessTokenExpireMs());
            addCookie(response, REFRESH_TOKEN_COOKIE, newRefresh, jwtTokenProvider.getRefreshTokenExpireMs());

            meterRegistry.counter("auth.reissue", "result", "success").increment();
            log.info("[reissue] success userId={}", userId);

        } else if (rotateResult == 2) {
            // 유예창 내 '선의의 패자': Redis엔 승자의 refresh만 존재한다.
            // 여기서 refresh 쿠키를 새로 심으면 쿠키≠Redis 불일치 → 다음 재발급에서 탈취 오탐(-1) → 전체 로그아웃.
            // 따라서 access 만 새로 발급해 원요청 재시도가 되게 하고, refresh 쿠키는 승자값 그대로 둔다.
            User user = userRepository.findById(userId).orElseThrow(this::unauthorized);
            String newAccess = jwtTokenProvider.createAccessToken(userId, user.getRole());

            addCookie(response, ACCESS_TOKEN_COOKIE, newAccess, jwtTokenProvider.getAccessTokenExpireMs());
            // ⚠️ REFRESH_TOKEN_COOKIE 는 갱신하지 않는다 (정합성 유지).

            meterRegistry.counter("auth.reissue", "result", "grace").increment();
            log.info("[reissue] grace-accept userId={}", userId);

        } else if (rotateResult == -1) {
            // 보안 경고 (탈취 의심)
            meterRegistry.counter("auth.reissue", "result", "theft_detected").increment();
            log.warn("[SECURITY] Refresh Token Reuse Detected! Session cleared. userId={}, sid={}", userId, sessionId);
            throw unauthorized();
        } else {
            // 0 (만료 또는 로그아웃됨)
            meterRegistry.counter("auth.reissue", "result", "reject").increment();
            throw unauthorized();
        }
    }

    /**
     * 로그아웃. refresh 쿠키에서 userId를 뽑아 Redis에서 지우고, 두 쿠키를 만료시킨다.
     * access가 만료된 상태에서도 로그아웃할 수 있도록 refresh 기반으로 처리한다.
     */
    public void logout(String refreshToken, HttpServletResponse response) {
        if (refreshToken != null && jwtTokenProvider.validate(refreshToken)
                && jwtTokenProvider.isRefreshToken(refreshToken)) {
            refreshTokenStore.delete(
                    jwtTokenProvider.getUserId(refreshToken),
                    jwtTokenProvider.getSessionId(refreshToken));   // 이 세션만
        }
        // 토큰이 이미 무효여도 쿠키는 확실히 지운다.
        expireCookie(response, ACCESS_TOKEN_COOKIE);
        expireCookie(response, REFRESH_TOKEN_COOKIE);
    }



    /**
     * 심사/제출용 리뷰어 로그인. OAuth를 건너뛰고 고정 리뷰어 유저로 토큰을 발급한다.
     * 반드시 app.auth.test-login.enabled=true 인 환경에서만 동작한다(운영 기본 false).
     */
    @Transactional
    public void testLogin(String key, HttpServletResponse response) {
        // 1) 꺼져 있으면 존재 자체를 숨긴다(404).
        if (!testLoginEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        // 2) 시크릿이 설정돼 있으면 일치해야 한다(선택적 2차 방어).
        if (testLoginSecret != null && !testLoginSecret.isBlank()
                && !testLoginSecret.equals(key)) {
            throw unauthorized();
        }
        // 3) 리뷰어 유저 find-or-create. 온보딩을 건너뛰도록 닉네임을 미리 세팅한다.
        User reviewer = userRepository
                .findByProviderAndProviderId(REVIEWER_PROVIDER, REVIEWER_PROVIDER_ID)
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .provider(REVIEWER_PROVIDER)
                                .providerId(REVIEWER_PROVIDER_ID)
                                .nickname("심사리뷰어")
                                .email("reviewer@catcheat.test")
                                .role(Role.ADMIN)
                                .build()));
        Long userId = reviewer.getId();

        // 4) 소셜 로그인과 동일하게 sid 세션으로 토큰 발급 → 다기기도 그대로 동작.
        String sessionId = java.util.UUID.randomUUID().toString();
        String access = jwtTokenProvider.createAccessToken(userId, reviewer.getRole());
        String refresh = jwtTokenProvider.createRefreshToken(userId, sessionId);
        refreshTokenStore.save(userId, sessionId, refresh);

        addCookie(response, ACCESS_TOKEN_COOKIE, access, jwtTokenProvider.getAccessTokenExpireMs());
        addCookie(response, REFRESH_TOKEN_COOKIE, refresh, jwtTokenProvider.getRefreshTokenExpireMs());

        meterRegistry.counter("auth.test_login").increment();
        log.info("[test-login] reviewer login userId={}, sid={}", userId, sessionId);
    }




    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증에 실패했습니다.");
    }

    private void addCookie(HttpServletResponse response, String name, String value, long maxAgeMs) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieSecure)   // 운영(HTTPS) true / 로컬(http) false
                .path("/")
                .maxAge(Duration.ofMillis(maxAgeMs))
                // projectjm.co.kr과 api.projectjm.co.kr은 등록 도메인이 같아 same-site다.
                // 크로스 오리진이어도 Lax로 쿠키가 실려 가므로 None까지 풀 이유가 없다
                .sameSite("Lax")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    /** maxAge=0으로 세팅해 브라우저가 쿠키를 즉시 삭제하게 한다. */
    private void expireCookie(HttpServletResponse response, String name) {
        ResponseCookie cookie = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
    /** 인증된 사용자의 내 정보 조회. */
    @Transactional(readOnly = true)
    public UserResponseDTO getMyInfo(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(this::unauthorized);
        return UserResponseDTO.from(user, onboardingService.seenGuideKeys(userId));
    }
}