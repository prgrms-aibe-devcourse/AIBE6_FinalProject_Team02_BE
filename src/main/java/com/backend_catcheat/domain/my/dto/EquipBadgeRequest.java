package com.backend_catcheat.domain.my.dto;

/**
 * 대표 뱃지 장착 요청. badgeId가 null이면 장착 해제.
 */
public record EquipBadgeRequest(Long badgeId) {}
