package com.backend_catcheat.domain.user.dto;

import com.backend_catcheat.domain.friend.entity.type.RelationStatus;

/** 유저 검색 결과 항목 */
public record UserSearchResultDTO(UserBriefDTO user, RelationStatus relationStatus) {}
