package com.backend_catcheat.domain.auth.entity;
/**
 * 소셜 로그인 제공자. 기획 확정 사항: 구글 / 카카오 / 네이버 3종.
 *
 * from() 메서드가 필요한 이유:
 * Spring Security OAuth2는 로그인한 제공자를 "registrationId"라는 소문자 문자열
 * (application.yml의 registration 키: "google", "kakao", "naver")로 넘겨준다.
 * 이 문자열을 우리 enum 값으로 변환할 때 사용한다.
 */
public enum Provider {
    GOOGLE,
    KAKAO,
    NAVER;

    /**
     * registrationId(예: "kakao")를 Provider enum(KAKAO)으로 변환한다.
     * 대소문자를 맞추기 위해 toUpperCase() 후 valueOf 한다.
     */
    public static Provider from(String registrationId) {
        return Provider.valueOf(registrationId.toUpperCase());
    }
}
