package com.backend_catcheat.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * Swagger(OpenAPI) 문서 설정.
 *
 * 인증이 httpOnly 쿠키라 Swagger의 Authorize 버튼으로는 토큰을 넣을 수 없다.
 * 대신 문서와 API가 같은 오리진(8080)이라, test-login을 한 번 호출하면
 * 브라우저가 쿠키를 물고 이후 요청에 자동으로 실어 보낸다 — 그 절차를 description에 적어 둔다.
 */
@Configuration
public class OpenApiConfig {

    static {
        // @AuthenticationPrincipal Long userId는 JWT 필터가 채우는 값이지 클라이언트 입력이 아니다.
        // 빼지 않으면 거의 모든 API에 userId 파라미터가 문서에 뜬다
        SpringDocUtils.getConfig().addAnnotationsToIgnore(AuthenticationPrincipal.class);
    }

    @Bean
    public OpenAPI catcheatOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("캣칫 CatchEat API")
                .version("v1")
                .description("""
                        먹은 음식을 사진으로 남겨 도감으로 모으는 수집형 기록 서비스.

                        ### 인증
                        `access_token` **httpOnly 쿠키**를 쓴다. 브라우저만 담을 수 있어 Authorize 버튼으로는 넣지 못한다.

                        Swagger에서 시험하려면 `POST /api/v1/auth/test-login` 을 먼저 호출한다.
                        문서와 API가 같은 오리진이라 이후 요청에는 쿠키가 자동으로 실린다.
                        (`TEST_LOGIN_ENABLED=true` 인 환경에서만 동작하고, 꺼져 있으면 404를 돌려준다)

                        ### 응답 형태
                        모든 응답은 `{ success, data, error }` 로 감싼다. 실패는 `error.code`(기계용) 와
                        `error.message`(사용자 노출용) 로 나뉜다.
                        """));
    }
}
