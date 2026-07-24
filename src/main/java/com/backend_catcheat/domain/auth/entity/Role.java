package com.backend_catcheat.domain.auth.entity;
/**
 * 서비스 권한 등급. 기획 확정 사항: USER / ADMIN 2종만 사용한다.
 *
 * authority 문자열에 "ROLE_" 접두사를 붙이는 이유:
 * Spring Security는 권한을 검사할 때 관례적으로 "ROLE_" 접두사를 기대한다.
 * 예) hasRole("ADMIN") 은 내부적으로 "ROLE_ADMIN" 권한이 있는지 확인한다.
 * 그래서 enum 이름(USER/ADMIN)과 실제 권한 문자열(ROLE_USER/ROLE_ADMIN)을 분리해 둔다.
 */

public enum Role {
    USER("ROLE_USER"),
    ADMIN("ROLE_ADMIN");

    private final String authority;

    Role(String authority){
        this.authority = authority;
    }
    public String getAuthority() {
        return authority;
    }
}
