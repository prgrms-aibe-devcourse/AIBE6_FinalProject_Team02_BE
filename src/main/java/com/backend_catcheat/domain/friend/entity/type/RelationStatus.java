package com.backend_catcheat.domain.friend.entity.type;

/** 나와 상대 유저의 관계 (프로필/검색 응답 → FE 버튼 4상태) */
public enum RelationStatus {
    SELF,               // 본인
    NONE,               // 관계 없음
    REQUEST_SENT,       // 내가 요청함(대기)
    REQUEST_RECEIVED,   // 상대가 나에게 요청함(대기)
    FRIEND              // 친구
}
