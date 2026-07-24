package com.backend_catcheat.domain.auth.entity;

import com.backend_catcheat.global.jpa.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 서비스 사용자(회원) 엔티티.
 *
 * 설계 포인트:
 * - 소셜 로그인만 지원하므로 password 필드가 없다.
 * - (provider + providerId) 조합이 사용자 식별 기준이다.
 *   예) 같은 이메일이라도 "구글로 가입한 사람"과 "카카오로 가입한 사람"은 다른 회원으로 본다.
 *   이 조합에 유니크 제약을 걸어 중복 가입을 DB 차원에서 막는다.
 *
 * 테이블명을 "users"로 두는 이유:
 * "user"는 PostgreSQL 예약어라 테이블명으로 쓰면 충돌이 날 수 있어 복수형 "users"를 쓴다.
 *
 * Lombok/JPA 관례 (AGENTS.md 코드 스타일 준수):
 * - @Setter 금지: 아무 데서나 값이 바뀌면 추적이 어렵다. 대신 의도가 드러나는
 *   메서드(updateOAuthProfile, withdraw)로만 상태를 바꾼다.
 * - @NoArgsConstructor(PROTECTED): JPA는 기본 생성자가 필요하지만, 외부에서
 *   빈 객체를 함부로 만들지 못하게 protected로 막는다.
 * - @Builder: 객체 생성 시 어떤 값을 넣는지 이름으로 명확히 드러낸다.
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
}





