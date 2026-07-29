package com.backend_catcheat.domain.auth.entity;
/**
 * 서비스 권한 등급. 기획 확정 사항: USER / ADMIN 2종만 사용한다.
 */

public enum Role {

    //값을 따로 두는 이유 : spring security에서 "ROLE_" 접두사를 기대하기때문에
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
