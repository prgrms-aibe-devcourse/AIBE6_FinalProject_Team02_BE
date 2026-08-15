package com.backend_catcheat.domain.auth.service;

import com.backend_catcheat.domain.auth.dto.UserResponseDTO;
import com.backend_catcheat.domain.auth.entity.User;
import com.backend_catcheat.domain.auth.repository.UserRepository;
import com.backend_catcheat.domain.auth.token.RefreshTokenStore;
import com.backend_catcheat.global.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

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
    private final MeterRegistry meterRegistry;

    /** 로그인 때 구운 쿠키와 같은 값이어야 한다. 다르면 재발급이 Secure를 벗겨 덮어쓴다 */
    @Value("${app.auth.cookie-secure}")
    private boolean cookieSecure;

    /**
     * access token 재발급.
     * 실패 사유는 보안상 뭉뚱그려 401로만 응답한다(비기능 요구사항).
     */
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
        String newRefresh = jwtTokenProvider.createRefreshToken(userId);
        boolean rotated = refreshTokenStore.rotate(userId, refreshToken, newRefresh);
        if (!rotated) {
            meterRegistry.counter("auth.reissue", "result", "reject").increment();
            log.warn("[reissue] reject userId={}", userId);
            throw unauthorized();
        }

        // 3) 회전 승자만 새 access 발급(role은 refresh에 없어 DB 조회).
        User user = userRepository.findById(userId).orElseThrow(this::unauthorized);
        String newAccess = jwtTokenProvider.createAccessToken(userId, user.getRole());

        addCookie(response, ACCESS_TOKEN_COOKIE, newAccess, jwtTokenProvider.getAccessTokenExpireMs());
        addCookie(response, REFRESH_TOKEN_COOKIE, newRefresh, jwtTokenProvider.getRefreshTokenExpireMs());

        meterRegistry.counter("auth.reissue", "result", "success").increment();
        log.info("[reissue] success userId={}", userId);
    }

    /**
     * 로그아웃. refresh 쿠키에서 userId를 뽑아 Redis에서 지우고, 두 쿠키를 만료시킨다.
     * access가 만료된 상태에서도 로그아웃할 수 있도록 refresh 기반으로 처리한다.
     */
    public void logout(String refreshToken, HttpServletResponse response) {
        if (refreshToken != null
                && jwtTokenProvider.validate(refreshToken)
                && jwtTokenProvider.isRefreshToken(refreshToken)) {
            refreshTokenStore.delete(jwtTokenProvider.getUserId(refreshToken));
        }
        // 토큰이 이미 무효여도 쿠키는 확실히 지운다.
        expireCookie(response, ACCESS_TOKEN_COOKIE);
        expireCookie(response, REFRESH_TOKEN_COOKIE);
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
        return UserResponseDTO.from(user);
    }
}