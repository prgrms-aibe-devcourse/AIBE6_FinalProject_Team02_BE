package com.backend_catcheat.domain.my.dto;

import java.time.LocalDateTime;

/**
 * 마이페이지 프로필 응답
 */
public record MyProfileResponse(
        String nickname,
        boolean nicknameChangeable,
        LocalDateTime nicknameChangeableAt,
        String profileImageUrl
) {}
