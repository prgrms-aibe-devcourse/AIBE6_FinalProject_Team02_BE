package com.backend_catcheat.domain.badge.entity;

import com.backend_catcheat.domain.badge.entity.type.BadgeConditionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    /** 뱃지 이미지 경로/URL (커스텀 뱃지) */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** FE 시스템 뱃지 식별자 */
    @Column(length = 64)
    private String code;

    /** 지급조건 유형 */
    @Enumerated(EnumType.STRING)
    @Column(name = "condition_type", length = 30)
    private BadgeConditionType conditionType;

    /** 지급조건 파라미터 */
    @Column(name = "condition_value", length = 64)
    private String conditionValue;

    /** 획득 조건 설명 문구 */
    @Column(length = 200)
    private String description;

    /** 운영진 마스터 뱃지 여부 */
    @Column(name = "is_system", nullable = false)
    private boolean isSystem;

    /** 보관함 정렬 순서 */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
