package com.backend_catcheat.domain.auth.service;

import com.backend_catcheat.domain.auth.dto.UserResponseDTO;
import com.backend_catcheat.domain.auth.entity.User;
import com.backend_catcheat.domain.auth.repository.UserRepository;
import com.backend_catcheat.domain.auth.token.RefreshTokenStore;
import com.backend_catcheat.global.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";
    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final UserRepository userRepository;

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

        // 2) Redis에 저장된 값과 대조. 없으면(만료/로그아웃) 또는 다르면(탈취 의심) 거부.
        String stored = refreshTokenStore.find(userId).orElseThrow(this::unauthorized);
        if (!stored.equals(refreshToken)) {
            throw unauthorized();
        }

        // 3) role은 refresh token에 없으므로 DB에서 사용자 조회로 가져온다.
        User user = userRepository.findById(userId).orElseThrow(this::unauthorized);

        // 4) 새 access + refresh 발급(refresh 회전) 후 Redis 갱신.
        //    회전: 재발급 때마다 refresh도 새로 발급해 탈취된 옛 토큰을 무효화한다.
        String newAccess = jwtTokenProvider.createAccessToken(userId, user.getRole());
        String newRefresh = jwtTokenProvider.createRefreshToken(userId);
        refreshTokenStore.save(userId, newRefresh);

        addCookie(response, ACCESS_TOKEN_COOKIE, newAccess, jwtTokenProvider.getAccessTokenExpireMs());
        addCookie(response, REFRESH_TOKEN_COOKIE, newRefresh, jwtTokenProvider.getRefreshTokenExpireMs());
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
                .secure(false)      // 운영(HTTPS)에서는 true
                .path("/")
                .maxAge(Duration.ofMillis(maxAgeMs))
                .sameSite("Lax")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    /** maxAge=0으로 세팅해 브라우저가 쿠키를 즉시 삭제하게 한다. */
    private void expireCookie(HttpServletResponse response, String name) {
        ResponseCookie cookie = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(false)
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