package com.backend_catcheat.domain.made.repository;

import com.backend_catcheat.domain.made.entity.MadeDexInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface MadeDexInviteRepository extends JpaRepository<MadeDexInvite, Long> {

    Optional<MadeDexInvite> findByCode(String code);

    boolean existsByCode(String code);

    /**
     * 참여 처리에서 그룹 행을 잠근 뒤 코드가 아직 살아 있는지 다시 확인할 때 쓴다.
     * 엔티티를 다시 읽으면 영속성 컨텍스트가 들고 있던 인스턴스가 그대로 돌아와
     * 다른 트랜잭션이 커밋한 revoked_at이 보이지 않는다. 스칼라 조회라야 DB를 다시 본다.
     */
    @Query("""
            select count(i) > 0
            from MadeDexInvite i
            where i.code = :code
              and i.revokedAt is null
              and i.expiresAt > :now
            """)
    boolean existsUsableByCode(@Param("code") String code, @Param("now") LocalDateTime now);

    /**
     * 그룹의 현재 유효 코드. 유효 코드는 그룹당 1개지만, 과거에 무효화된 행이 쌓이므로
     * revoked_at IS NULL + 미만료로 거른다. 최신순은 만에 하나 두 행이 살아남았을 때의 방어.
     */
    Optional<MadeDexInvite> findFirstByMadeDexIdAndRevokedAtIsNullAndExpiresAtAfterOrderByIdDesc(
            Long madeDexId, LocalDateTime now);

    /** 재발급 직전 호출. 살아 있는 코드를 한 번에 죽인다 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update MadeDexInvite i
               set i.revokedAt = :now
             where i.madeDexId = :madeDexId
               and i.revokedAt is null
            """)
    int revokeActive(@Param("madeDexId") Long madeDexId, @Param("now") LocalDateTime now);
}
