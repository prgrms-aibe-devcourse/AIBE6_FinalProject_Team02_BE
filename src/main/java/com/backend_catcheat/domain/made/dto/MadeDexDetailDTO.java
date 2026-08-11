package com.backend_catcheat.domain.made.dto;

import com.backend_catcheat.domain.made.entity.MadeDexRole;

import java.time.LocalDateTime;

// 로그잇은 비공개 전용이라 멤버만 열람한다. myRole은 항상 채워진다
public record MadeDexDetailDTO(
        Long madeDexId,
        String name,
        String description,
        String imageUrl,
        // 수정 화면이 표지를 그대로 둘 때 되돌려 보낸다. presigned URL 경로에 이미 드러나는 값이다
        String imageKey,
        long memberCount,
        int maxMembers,
        MadeDexRole myRole,
        LocalDateTime createdAt
) {}
