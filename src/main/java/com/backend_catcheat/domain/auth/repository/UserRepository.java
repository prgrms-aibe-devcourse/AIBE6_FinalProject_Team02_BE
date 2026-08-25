package com.backend_catcheat.domain.auth.repository;

import com.backend_catcheat.domain.auth.entity.Provider;
import com.backend_catcheat.domain.auth.entity.Role;
import com.backend_catcheat.domain.auth.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.List;
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

    /** 관리자 계정 조회 */
    List<User> findAllByRole(Role role);

    /**
     * 개설권 차감처럼 "읽고→검사→쓰기"가 원자적이어야 하는 경우 유저 행을 잠그고 읽는다.
     * 같은 유저의 동시 요청을 직렬화해 카운터가 어긋나는 것을 막는다(PESSIMISTIC_WRITE).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    /**
     * 닉네임 중복 여부
     * 초기 세팅·변경 시 유니크 보장을 위해 사용
     */
    boolean existsByNickname(String nickname);

    /**
     * 닉네임 검색(본인·탈퇴 제외)
     */
    @Query("select u from User u "
            + "where u.id <> :meId and u.deletedAt is null "
            + "and u.nickname like concat('%', :keyword, '%') "
            + "order by u.nickname asc")
    List<User> searchByNicknameContaining(@Param("keyword") String keyword,
                                          @Param("meId") Long meId,
                                          Pageable pageable);
}
