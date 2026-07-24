package com.backend_catcheat.domain.auth.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
public class User {

}
