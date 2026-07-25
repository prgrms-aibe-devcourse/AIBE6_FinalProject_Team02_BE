package com.backend_catcheat.global.security;

import com.backend_catcheat.domain.auth.oauth.CustomOAuth2User;
import com.backend_catcheat.domain.auth.token.RefreshTokenStore;
import com.backend_catcheat.global.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

/**
 * 소셜 로그인 성공 순간 실행된다.
 * 1) CustomOAuth2User에서 userId/role을 꺼내 access·refresh JWT 발급
 * 2) refresh token을 Redis에 저장(재발급 대조/로그아웃 무효화용)
 * 3) 두 토큰을 httpOnly 쿠키로 응답에 심고
 * 4) 프론트 콜백 주소로 리다이렉트 (토큰은 쿠키에 있으니 URL엔 노출 안 됨)
 */
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;

    @Value("${app.oauth2.authorized-redirect-uri}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication // 로그인 성공한 사용자 정보가 담긴 객체
    ) throws IOException {

        CustomOAuth2User principal = (CustomOAuth2User) authentication.getPrincipal();
        Long userId = principal.getUserId();

        // 1) 토큰 발급
        String accessToken = jwtTokenProvider.createAccessToken(userId, principal.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);

        // 2) refresh token은 서버(Redis)에도 저장
        refreshTokenStore.save(userId, refreshToken);
        // 3) 두 토큰을 httpOnly 쿠키로 심는다
        addCookie(response, ACCESS_TOKEN_COOKIE, accessToken,
                jwtTokenProvider.getAccessTokenExpireMs());
        addCookie(response, REFRESH_TOKEN_COOKIE, refreshToken,
                jwtTokenProvider.getRefreshTokenExpireMs());

        // 4) 프론트로 리다이렉트
        getRedirectStrategy().sendRedirect(request, response, redirectUri);
    }

    private void addCookie(HttpServletResponse response, String name, String value, long maxAgeMs) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)          // JS에서 접근 불가 (XSS 방어)
                .secure(false)           // 운영(HTTPS)에서는 true로. 로컬 http 개발이라 우선 false
                .path("/")               // 모든 경로에서 전송
                .maxAge(Duration.ofMillis(maxAgeMs))
                .sameSite("Lax")         // CSRF 완화. 크로스 사이트 요청엔 쿠키 미전송
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}
