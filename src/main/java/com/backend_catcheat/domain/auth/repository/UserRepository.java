package com.backend_catcheat.domain.auth.repository;

import com.backend_catcheat.domain.auth.entity.Provider;
import com.backend_catcheat.domain.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * User 엔티티에 대한 데이터 접근 인터페이스.
 *
 * JpaRepository<User, Long> 를 상속하면 save(), findById(), delete() 등
 * 기본 CRUD 메서드가 자동으로 제공된다. (구현체는 Spring Data JPA가 런타임에 생성)
 */
public interface UserRepository extends JpaRepository<User, Long> {
    /**
     * 소셜 로그인 시 (provider + providerId)로 기존 회원을 찾는다.
     * 메서드 이름만 규칙대로 지으면 Spring Data JPA가 쿼리를 자동 생성한다.
     * (findBy + 필드명 조합 → WHERE provider = ? AND provider_id = ?)
     *
     * 반환이 Optional인 이유: 신규 사용자면 결과가 없을 수 있으므로,
     * 없을 때 null 대신 Optional.empty()로 안전하게 처리한다.
     */
    Optional<User> findByProviderAndProviderId(Provider provider, String providerId);
}
