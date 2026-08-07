package com.backend_catcheat.domain.user.dto;

import com.backend_catcheat.domain.friend.entity.type.RelationStatus;

/** 다른사람의 공개 프로필 */
public record PublicProfileDTO(UserBriefDTO user, RelationStatus relationStatus) {}
