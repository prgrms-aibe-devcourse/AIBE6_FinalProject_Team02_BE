package com.backend_catcheat.domain.made.dto;

import com.backend_catcheat.domain.made.entity.MadeDexRole;

import java.time.LocalDateTime;

public record MadeDexMemberDTO(
        Long userId,
        String nickname,
        String profileImageUrl,
        MadeDexRole role,
        LocalDateTime joinedAt,
        boolean me
) {}
