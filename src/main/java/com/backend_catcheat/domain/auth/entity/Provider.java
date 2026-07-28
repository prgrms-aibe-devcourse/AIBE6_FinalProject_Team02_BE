package com.backend_catcheat.domain.auth.entity;
/**
 * 소셜 로그인 제공자. 기획 확정 사항: 구글 / 카카오 / 네이버 3종.
 */
public enum Provider {
    GOOGLE,
    KAKAO,
    NAVER;
    //spring security에서 소문자로 provider를 반환하기 때문에 from메서드를 사용
    public static Provider from(String registrationId) {
        return Provider.valueOf(registrationId.toUpperCase());
    }
}
