package com.backend_catcheat.domain.friend.dto;

import com.backend_catcheat.domain.user.dto.UserBriefDTO;

/** 요청 목록 항목 */
public record ReceivedRequestDTO(Long requestId, UserBriefDTO user) {}
