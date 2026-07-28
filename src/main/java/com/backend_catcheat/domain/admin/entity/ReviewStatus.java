package com.backend_catcheat.domain.admin.entity;

/** 수동 등록 건의 검토 상태 */
public enum ReviewStatus {
    /** 관리자 확인 대기. 이 동안 도감 칸은 열리지 않는다 */
    PENDING,
    /** 수락 — 이 시점에 칸이 열리고 수집률에 반영된다 */
    APPROVED,
    /** 반려 — 칸은 끝내 열리지 않는다 */
    REJECTED
}
