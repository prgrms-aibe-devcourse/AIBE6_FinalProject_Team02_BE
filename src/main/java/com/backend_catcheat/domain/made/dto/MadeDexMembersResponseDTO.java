package com.backend_catcheat.domain.made.dto;

import com.backend_catcheat.domain.made.entity.MadeDexRole;

import java.util.List;

// 그룹 이름과 내 역할을 함께 준다. 화면이 목록을 그리려고 그룹 조회를 또 하지 않게 한다
public record MadeDexMembersResponseDTO(
        Long madeDexId,
        String name,
        int maxMembers,
        MadeDexRole myRole,
        List<MadeDexMemberDTO> members
) {}
