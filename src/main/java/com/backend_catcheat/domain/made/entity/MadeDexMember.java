package com.backend_catcheat.domain.made.entity;

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

import java.time.LocalDateTime;

// 테이블에 joined_at만 있고 감사 컬럼이 없어 BaseEntity를 상속하지 않는다
@Entity
@Table(name = "made_dex_member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MadeDexMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "made_dex_id", nullable = false)
    private Long madeDexId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private MadeDexRole role;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    private MadeDexMember(Long madeDexId, Long userId, MadeDexRole role, LocalDateTime joinedAt) {
        this.madeDexId = madeDexId;
        this.userId = userId;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public static MadeDexMember owner(Long madeDexId, Long userId, LocalDateTime joinedAt) {
        return new MadeDexMember(madeDexId, userId, MadeDexRole.OWNER, joinedAt);
    }

    /** 초대 코드로 합류한 일반 멤버 */
    public static MadeDexMember member(Long madeDexId, Long userId, LocalDateTime joinedAt) {
        return new MadeDexMember(madeDexId, userId, MadeDexRole.MEMBER, joinedAt);
    }

    public boolean isOwner() {
        return role == MadeDexRole.OWNER;
    }

    public void promoteToOwner() {
        this.role = MadeDexRole.OWNER;
    }

    public void demoteToMember() {
        this.role = MadeDexRole.MEMBER;
    }
}
