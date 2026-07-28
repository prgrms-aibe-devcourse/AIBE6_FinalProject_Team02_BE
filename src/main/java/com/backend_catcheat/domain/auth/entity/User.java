package com.backend_catcheat.domain.auth.entity;

import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import com.backend_catcheat.global.jpa.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 서비스 사용자(회원) 엔티티.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_provider",
                        columnNames = {"provider", "provider_id"}
                )
        }
)
public class User extends BaseEntity {
    /** 소셜 제공자(GOOGLE/KAKAO/NAVER). EnumType.STRING = DB에 "KAKAO"처럼 문자열로 저장. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Provider provider;

    /** 제공자 시스템에서의 고유 사용자 id (예: 카카오 회원번호). */
    @Column(name = "provider_id", nullable = false)
    private String providerId;

    /** 닉네임. 소셜에서 받은 값으로 초기 세팅. 미제공일 수 있어 nullable. */
    @Column(length = 30)
    private String nickname;

    /** 이메일. 소셜에서 받은 값. length 320 = 이메일 최대 길이. */
    @Column(length = 320)
    private String email;

    /** 권한. 신규 가입 시 USER로 부여한다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /**
            * 탈퇴 시각. null이면 활성 회원, 값이 있으면 탈퇴한 회원(Soft Delete).
            * 즉시 삭제하지 않는 이유: 기획상 유예 기간(예: 30일) 후 파기 정책이 있고,
     * 감사(audit) 목적으로도 흔적을 남긴다.
            */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** 온보딩 튜토리얼 완료 여부 */
    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted;

    /**
     * 닉네임 최종 변경 시각
     */
    @Column(name = "nickname_updated_at")
    private LocalDateTime nicknameUpdatedAt;

    /** 장착한 대표 뱃지 id. 닉네임 옆에 표시된다. null이면 미장착. (badge 삭제 시 FK가 NULL 처리) */
    @Column(name = "equipped_badge_id")
    private Long equippedBadgeId;

    /** 프로필 사진 S3 object key. null이면 사진 없음 */
    @Column(name = "profile_image_key", length = 512)
    private String profileImageKey;

    /**
     * 생성자를 private + @Builder로 둔다.
     * 외부에서는 User.builder().provider(...).build() 형태로만 생성하게 강제한다.
     */
    @Builder
    private User(Provider provider, String providerId, String nickname, String email, Role role) {
        this.provider = provider;
        this.providerId = providerId;
        this.nickname = nickname;
        this.email = email;
        this.role = role;
    }

    /**
     * 소셜 로그인 시 이메일만 최신값으로 갱신한다.
     * 닉네임은 유저가 직접 지정하므로 소셜 값으로 덮어쓰지 않는다.
     */
    public void updateEmail(String email) {
        if (email != null) {
            this.email = email;
        }
    }

    /**
     * 회원 탈퇴 처리 — 개인정보를 지우고(비식별화) 탈퇴 시각을 기록한다.
     * 실제 행(row) 삭제는 유예 기간 후 별도 배치에서 수행한다.
     */
    public void withdraw() {
        this.deletedAt = LocalDateTime.now();
        this.nickname = null;
        this.email = null;
    }

    /** 탈퇴한 회원인지 여부. */
    public boolean isWithdrawn() {
        return this.deletedAt != null;
    }

    /** 온보딩 튜토리얼 완료 처리 */
    public void completeOnboarding() {
        this.onboardingCompleted = true;
    }

    /**
     * 최초 닉네임 세팅
     */
    public void setInitialNickname(String nickname) {
        if (this.nickname != null) {
            throw new CustomException(ErrorCode.NICKNAME_ALREADY_SET);
        }
        this.nickname = nickname;
    }

    /**
     * 닉네임 변경
     * 마지막 변경 후 1개월이 지나지 않았으면 예외
     */
    public void changeNickname(String nickname, LocalDateTime now) {
        if (this.nicknameUpdatedAt != null && now.isBefore(this.nicknameUpdatedAt.plusMonths(1))) {
            throw new CustomException(ErrorCode.NICKNAME_CHANGE_TOO_SOON);
        }
        this.nickname = nickname;
        this.nicknameUpdatedAt = now;
    }

    /** 대표 뱃지 장착/해제. badgeId가 null이면 해제. (보유 검증은 서비스에서 선행) */
    public void equipBadge(Long badgeId) {
        this.equippedBadgeId = badgeId;
    }

    /** 프로필 사진 설정(업로드된 S3 key) */
    public void changeProfileImage(String key) {
        this.profileImageKey = key;
    }

    /** 프로필 사진 제거 → 닉네임 첫 글자 표시로 돌아감 */
    public void removeProfileImage() {
        this.profileImageKey = null;
    }
}





