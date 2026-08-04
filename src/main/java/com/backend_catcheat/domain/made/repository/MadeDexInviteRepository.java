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
