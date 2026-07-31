package com.backend_catcheat.domain.badge.entity.type;

/**
 * 뱃지 지급조건 유형
 */
public enum BadgeConditionType {
    /** 가입 시 지급 */
    SIGNUP,
    /** 기본 도감 수집률 임계 (value = 퍼센트: 25/50/75/100) */
    COLLECTION_RATE,
    /** 특정 카테고리 전량 수집 (value = basic_dex.category) */
    CATEGORY_COMPLETE,
    /** 첫 제작 도감 개설 */
    FIRST_MADE_DEX,
    /** 챌린지 보상 프리셋 템플릿 -> 자동 지급 대상 아님(개설 시 복제해서 사용) */
    CHALLENGE_PRESET
}
