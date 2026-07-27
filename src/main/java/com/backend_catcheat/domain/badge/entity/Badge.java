package com.backend_catcheat.domain.badge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 뱃지 마스터
 * 운영진 미션 보상 또는 챌린지 개설자가 만든 보상 뱃지
 */
@Entity
@Table(name = "badge")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Badge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    /** 뱃지 이미지 경로/URL. 정적 에셋이거나(운영진) 커스텀 업로드 S3 URL(챌린지). null이면 프론트가 아이콘으로 대체. */
    @Column(name = "image_url", length = 500)
    private String imageUrl;
}
