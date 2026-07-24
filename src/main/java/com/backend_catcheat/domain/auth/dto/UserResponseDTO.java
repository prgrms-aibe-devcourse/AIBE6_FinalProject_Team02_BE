package com.backend_catcheat.domain.auth.dto;

import com.backend_catcheat.domain.auth.entity.User;

/**
 * 내 정보 응답 DTO. 엔티티를 직접 노출하지 않고 필요한 필드만 담는다(AGENTS.md 계층 규칙).
 */
public record UserResponseDTO(Long id, String nickname, String email, String role) {

    public static UserResponseDTO from(User user) {
        return new UserResponseDTO(
                user.getId(),
                user.getNickname(),
                user.getEmail(),
                user.getRole().name()
        );
    }
}