package com.backend_catcheat.domain.auth.dto;

import com.backend_catcheat.domain.auth.entity.User;

import java.util.List;

/** 내 정보 응답 DTO */
public record UserResponseDTO(
        Long id,
        String nickname,
        String email,
        String role,
        List<String> seenGuides
) {
    /** 가이드 정보가 필요 없는 자리용 */
    public static UserResponseDTO from(User user) {
        return from(user, List.of());
    }

    public static UserResponseDTO from(User user, List<String> seenGuides) {
        return new UserResponseDTO(
                user.getId(),
                user.getNickname(),
                user.getEmail(),
                user.getRole().name(),
                seenGuides
        );
    }
}
