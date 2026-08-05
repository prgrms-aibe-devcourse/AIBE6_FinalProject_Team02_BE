package com.backend_catcheat.domain.made.dto;

import com.backend_catcheat.domain.made.entity.MadeDexRole;
import com.backend_catcheat.domain.made.entity.Visibility;

import java.time.LocalDateTime;

// myRole은 멤버가 아니면 null. 공개 도감은 참여하지 않아도 열람할 수 있다
public record MadeDexDetailDTO(
        Long madeDexId,
        String name,
        String description,
        Visibility visibility,
        String imageUrl,
        // 수정 화면이 표지를 그대로 둘 때 되돌려 보낸다. presigned URL 경로에 이미 드러나는 값이다
        String imageKey,
        long memberCount,
        int maxMembers,
        MadeDexRole myRole,
        LocalDateTime createdAt
) {}
