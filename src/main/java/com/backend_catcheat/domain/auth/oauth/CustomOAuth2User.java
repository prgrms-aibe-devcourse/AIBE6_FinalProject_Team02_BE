package com.backend_catcheat.domain.auth.oauth;

import com.backend_catcheat.domain.auth.entity.Role;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 로그인 성공 후 SecurityContext에 저장되는 인증 주체(principal).
 * 우리가 필요한 건 "우리 DB의 userId"와 "role"이다.
 * 이후 로그인 성공 핸들러에서 이 값들을 꺼내 JWT를 발급한다.
 */
@Getter
public class CustomOAuth2User implements OAuth2User {

    private final Long userId;
    private final Role role;
    private final Map<String, Object> attributes;  // 프로바이더 원본 응답(디버깅/확장용)

    public CustomOAuth2User(Long userId, Role role, Map<String, Object> attributes) {
        this.userId = userId;
        this.role = role;
        this.attributes = attributes;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /** 이 사용자의 권한 목록. Role의 "ROLE_..." 문자열을 시큐리티 권한으로 변환한다. */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.getAuthority()));
    }

    /** OAuth2User가 요구하는 식별자. 우리는 userId를 문자열로 반환한다. */
    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
