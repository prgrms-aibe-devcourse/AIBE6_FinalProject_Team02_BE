package com.backend_catcheat.domain.made.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 초대 코드. 코드 하나로 만료 전까지 여러 명이 합류한다(단톡방 초대 링크 느낌).
 * 그룹당 유효한 코드는 1개고, 재발급하면 이전 행에 revokedAt이 찍힌다.
 *
 * made_dex_invite에 updated_at이 없어 BaseEntity를 상속하지 않는다 — MadeDexMember와 같은 이유.
 */
@Entity
@Table(name = "made_dex_invite")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MadeDexInvite {

    /** 발급 후 유효 기간 */
    public static final int TTL_DAYS = 7;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "made_dex_id", nullable = false)
    private Long madeDexId;

    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    private MadeDexInvite(Long madeDexId, String code, Long createdBy, LocalDateTime now) {
        this.madeDexId = madeDexId;
        this.code = code;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.expiresAt = now.plusDays(TTL_DAYS);
    }

    public static MadeDexInvite issue(Long madeDexId, String code, Long createdBy, LocalDateTime now) {
        return new MadeDexInvite(madeDexId, code, createdBy, now);
    }

    public boolean isUsableAt(LocalDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
