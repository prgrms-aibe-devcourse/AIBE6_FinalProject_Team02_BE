package com.backend_catcheat.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * 소셜 로그인 실패 시 실행된다.
 * 실패 사유를 자세히 노출하지 않고, 프론트 콜백 주소에 error 표시만 붙여 리다이렉트한다.
 * (비기능 요구사항: 인증 실패 사유를 과도하게 노출하지 않는다)
 */
@Component
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Value("${app.oauth2.authorized-redirect-uri}")
    private String redirectUri;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        // 기본은 사유를 숨기고(login_failed), 탈퇴 회원만 별도 코드로 안내한다.
        String errorCode = "login_failed";
        if (exception instanceof OAuth2AuthenticationException oae
                && "withdrawn_user".equals(oae.getError().getErrorCode())) {
            errorCode = "withdrawn";
        }

        String target = redirectUri + "?error=" +
                URLEncoder.encode(errorCode, StandardCharsets.UTF_8);
        getRedirectStrategy().sendRedirect(request, response, target);
    }
}