package com.backend_catcheat.domain.challenge.entity;

// 탐색 정렬 기준
public enum ChallengeSortType {
    LATEST,        // 최신 등록순(기본)
    VIEWS,         // 최근 7일 조회수
    PARTICIPANTS,  // 최근 7일 신규 참여
    UNLOCKS        // 최근 7일 해금 수
}
