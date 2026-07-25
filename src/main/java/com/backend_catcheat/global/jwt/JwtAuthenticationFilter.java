package com.backend_catcheat.global.jwt;

import com.backend_catcheat.domain.auth.entity.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 모든 요청에 대해 딱 한 번 실행되는 필터(OncePerRequestFilter).
 * httpOnly 쿠키에 담긴 access token을 꺼내 검증하고, 유효하면
 * "이 요청은 인증된 사용자"라고 SecurityContext에 등록한다.
 *
 * 쿠키 방식이므로 Authorization 헤더가 아니라 쿠키에서 토큰을 읽는다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    public static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain //  요청이 필터를 통과한 후 다음 필터/컨트롤러로 넘기기 위한 체인
    ) throws ServletException, IOException {

        String token = extractAccessToken(request);

        // 토큰이 있고 유효하면 인증 정보를 SecurityContext에 세팅한다.
        // (없거나 무효면 아무것도 안 하고 넘긴다 → 뒤에서 인가 검사가 401/403 처리)
        if (token != null && jwtTokenProvider.validate(token)) {
            Long userId = jwtTokenProvider.getUserId(token);
            Role role = jwtTokenProvider.getRole(token);

            // principal = userId, 권한 = role. 이후 컨트롤러에서 인증 주체를 이 값으로 식별한다.
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            List.of(new SimpleGrantedAuthority(role.getAuthority()))
                    );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        //doFilter : 다음 필터/컨트롤러로 요청을 넘긴다. (필수 호출)
        filterChain.doFilter(request, response);
    }

    /** 요청 쿠키들 중 access_token 쿠키의 값을 꺼낸다. 없으면 null. */
    private String extractAccessToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (ACCESS_TOKEN_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}

