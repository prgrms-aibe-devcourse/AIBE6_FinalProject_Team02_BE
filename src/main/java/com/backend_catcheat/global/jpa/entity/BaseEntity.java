package com.backend_catcheat.global.jpa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 모든 엔티티 공통 PK + 생성/수정 시각.
 * @MappedSuperclass: 테이블이 아니라 상속한 엔티티의 컬럼으로 합쳐진다.
 * @EntityListeners(AuditingEntityListener): 생성/수정 시각 자동 기록
 *   (BackendCatcheatApplication의 @EnableJpaAuditing이 있어야 동작 — 이미 있음)
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(name = "created_at", updatable = false)   // 테이블 컬럼명과 일치
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}