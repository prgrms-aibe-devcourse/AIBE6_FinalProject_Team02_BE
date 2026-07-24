package com.backend_catcheat.domain.auth.oauth;

import com.backend_catcheat.domain.auth.entity.Role;
import com.backend_catcheat.domain.auth.entity.User;
import com.backend_catcheat.domain.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 로그인이 성공하면 Spring Security가 이 서비스의 loadUser()를 호출한다.
 * 여기서 (provider + providerId)로 기존 회원을 찾고,
 *  - 있으면: 이메일만 최신화하고 그대로 사용(닉네임은 유저가 직접 지정하므로 건드리지 않음)
 *  - 없으면: 신규 가입시킨다(자동 회원가입). 닉네임은 온보딩에서 받을 것이므로 비워둔다.
 *
 * DefaultOAuth2UserService를 상속해 기본 동작(프로바이더에서 사용자 정보 조회)은 그대로 쓰고,
 * 그 뒤에 우리 DB 처리(조회/가입)만 얹는다.
 */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        // 1) 부모가 프로바이더에서 사용자 정보를 받아온다.
        OAuth2User oAuth2User = super.loadUser(userRequest);

        // 2) 어떤 프로바이더인지("google"/"kakao"/"naver") + 원본 응답을 파싱한다.
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuthAttributes attributes = OAuthAttributes.of(registrationId, oAuth2User.getAttributes());

        // 3) 기존 회원 조회 → 있으면 이메일 갱신, 없으면 신규 가입.
        User user = userRepository
                .findByProviderAndProviderId(attributes.provider(), attributes.providerId())
                .map(existing -> {
                    existing.updateEmail(attributes.email());
                    return existing;
                })
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .provider(attributes.provider())
                                .providerId(attributes.providerId())
                                // nickname은 유저가 온보딩에서 직접 지정 → 가입 시엔 비워둔다.
                                .email(attributes.email())
                                .role(Role.USER)   // 신규 가입은 항상 일반 사용자 권한
                                .build()
                ));

        // 4) 로그인 주체(principal)로 반환. userId/role을 담아 이후 JWT 발급에 사용한다.
        return new CustomOAuth2User(user.getId(), user.getRole(), oAuth2User.getAttributes());
    }
}
