package com.backend_catcheat.domain.my.dto;

/** 닉네임 사용 가능 여부 — 입력 중 실시간 판정용 */
public record NicknameAvailabilityResponse(
        boolean available
) {}
