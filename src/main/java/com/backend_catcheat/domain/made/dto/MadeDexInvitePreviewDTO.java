package com.backend_catcheat.domain.made.dto;

/**
 * 초대 링크로 들어온 사람에게 "어떤 그룹인지" 먼저 보여주기 위한 응답.
 * alreadyMember면 FE가 참여 버튼 대신 바로 그룹으로 보낸다.
 */
public record MadeDexInvitePreviewDTO(
        Long madeDexId,
        String name,
        String description,
        long memberCount,
        int maxMembers,
        boolean alreadyMember
) {
}
