package com.backend_catcheat.domain.onboarding.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 도메인별 온보딩을 본 기록
 * 행이 없으면 그 화면 첫 진입에서 자동 재생
 * User와 연관관계를 걸지 않고 userId만 가지고 있음
 * 조회가 "이 사람이 본 키 목록"뿐이라 연관관계를 걸면 얻는 것 없이 로딩만 늘어남
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_guide_seen")
@IdClass(UserGuideSeenId.class)
public class UserGuideSeen {
    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "guide_key", nullable = false, length = 40)
    private String guideKey;

    /** 본 시각. 가이드 문구를 개편했을 때 다시 보여줄 대상을 가르는 근거 */
    @Column(name = "seen_at", nullable = false)
    private LocalDateTime seenAt;
}
