package com.backend_catcheat.domain.auth.oauth;

import com.backend_catcheat.domain.auth.entity.Provider;

import java.util.Map;

/**
 * 프로바이더마다 사용자 정보 응답 구조가 달라서 파싱을 한 곳으로 모은다.
 * - Google: 최상위에 sub/name/email 이 바로 있다 (flat).
 * - Kakao : 최상위 id + kakao_account.email + kakao_account.profile.nickname (중첩).
 * - Naver : response 객체 안에 id/name/email 이 들어있다 (중첩).
 *
 * record: 값만 담는 불변 객체. of()로 만들어 provider/providerId/nickname/email을 꺼내 쓴다.
 */
public record OAuthAttributes(
        Provider provider,
        String providerId,
        String nickname,
        String email
) {

    /**
     * registrationId("google"/"kakao"/"naver")에 따라 알맞은 파서를 호출한다.
     * attributes는 Spring Security가 프로바이더에서 받아 넘겨주는 원본 응답(Map)이다.
     */
    public static OAuthAttributes of(String registrationId, Map<String, Object> attributes) {
        Provider provider = Provider.from(registrationId);
        return switch (provider) {
            case GOOGLE -> ofGoogle(attributes);
            case KAKAO -> ofKakao(attributes);
            case NAVER -> ofNaver(attributes);
        };
    }

    private static OAuthAttributes ofGoogle(Map<String, Object> attributes) {
        // 구글은 평평한 구조라 바로 꺼낸다. providerId는 "sub"(구글 고유 id).
        return new OAuthAttributes(
                Provider.GOOGLE,
                asString(attributes.get("sub")),
                asString(attributes.get("name")),
                asString(attributes.get("email"))
        );
    }

    @SuppressWarnings("unchecked")
    private static OAuthAttributes ofKakao(Map<String, Object> attributes) {
        // 카카오: 최상위 "id"가 회원번호(providerId).
        String providerId = asString(attributes.get("id"));

        // 이메일/닉네임은 kakao_account 안에 중첩되어 있고, 동의 여부에 따라 없을 수 있다.
        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
        String email = null;
        String nickname = null;
        if (kakaoAccount != null) {
            email = asString(kakaoAccount.get("email"));
            Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
            if (profile != null) {
                nickname = asString(profile.get("nickname"));
            }
        }
        return new OAuthAttributes(Provider.KAKAO, providerId, nickname, email);
    }

    @SuppressWarnings("unchecked")
    private static OAuthAttributes ofNaver(Map<String, Object> attributes) {
        // 네이버는 실제 정보가 "response" 객체 안에 들어있다.
        Map<String, Object> response = (Map<String, Object>) attributes.get("response");
        if (response == null) {
            throw new IllegalArgumentException("네이버 응답에 response 필드가 없습니다.");
        }
        return new OAuthAttributes(
                Provider.NAVER,
                asString(response.get("id")),
                asString(response.get("name")),
                asString(response.get("email"))
        );
    }

    /** 값이 숫자(예: 카카오 id는 Long)로 올 수 있어 문자열로 안전 변환한다. */
    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
