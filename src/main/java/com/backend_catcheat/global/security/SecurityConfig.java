package com.backend_catcheat.global.security;

import com.backend_catcheat.domain.auth.oauth.CustomOAuth2UserService;
import com.backend_catcheat.global.jwt.JwtAuthenticationFilter;
import com.backend_catcheat.global.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 전체 설정.
 * - 세션을 쓰지 않고(STATELESS) JWT 쿠키로 인증한다.
 * - 소셜 로그인(oauth2Login)에 우리 서비스/핸들러를 연결한다.
 * - 요청마다 JWT 필터가 먼저 돌아 인증을 세팅한다.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.auth.cookie-secure}")
    private boolean cookieSecure;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // SPA(React/Next.js) 환경을 위한 CSRF 설정
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        
        // [핵심 방어] 서브도메인 쿠키 주입 방어를 위한 Prefix 및 Secure 옵션 적용
        // 단, __Host- 접두사는 무조건 Secure(HTTPS) 속성이 필요하므로 로컬(HTTP) 환경과 분기 처리합니다.
        if (cookieSecure) {
            csrfTokenRepository.setCookieName("__Host-XSRF-TOKEN");
        } else {
            csrfTokenRepository.setCookieName("XSRF-TOKEN"); // 로컬 개발(http://localhost)용
        }
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie.secure(cookieSecure));

        // Spring Security 6.x 이상에서 CSRF 토큰을 매 요청마다 지연 없이 해석하기 위한 핸들러
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName(null);

        http
                // 기존의 .csrf(disable) 제거 후 아래 내용으로 교체
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(requestHandler)
                        // GET 요청은 제외되며, 아래 경로는 POST 등이어도 예외 처리
                        .ignoringRequestMatchers(
                                "/oauth2/**",
                                "/login/**",
                                "/api/v1/auth/reissue"
                        )
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // URL별 접근 권한
                .authorizeHttpRequests(auth -> auth
                        // 로그인/재발급 등 인증 없이 열어둘 경로
                        .requestMatchers(
                                "/",
                                "/oauth2/**",
                                "/login/**",
                                "/api/v1/auth/reissue",
                                "/api/v1/auth/logout"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/dex/basic").permitAll()
                        // 관리자 전용
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // 그 외 모든 요청은 인증 필요
                        .anyRequest().authenticated()
                )

                // 소셜 로그인 연결: 사용자 정보 처리 서비스 + 성공/실패 핸들러
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo ->
                                userInfo.userService(customOAuth2UserService))
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler(oAuth2FailureHandler)
                )

                // 우리 JWT 필터를 시큐리티 기본 인증 필터 앞에 끼운다
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    /** 프론트(다른 포트/도메인)에서 쿠키 포함 요청을 허용하기 위한 CORS 설정. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("https://projectjm.co.kr", "http://localhost:3000")); // 프론트 주소(운영 시 실제 도메인 추가)
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true); // 쿠키 전송 허용 (httpOnly 쿠키 인증에 필수)

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
