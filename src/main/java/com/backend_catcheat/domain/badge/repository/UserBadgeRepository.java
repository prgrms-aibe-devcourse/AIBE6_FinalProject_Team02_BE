package com.backend_catcheat.domain.badge.repository;

import com.backend_catcheat.domain.badge.entity.UserBadge;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserBadgeRepository extends JpaRepository<UserBadge, Long> {
    /** 유저의 획득 뱃지를 badge까지 함께(fetch) 최신순으로 조회한다. */
    @Query("select ub from UserBadge ub join fetch ub.badge"
            + " where ub.userId = :userId order by ub.acquiredAt desc")
    List<UserBadge> findWithBadgeByUserId(Long userId);

    /** 유저가 해당 뱃지를 보유했는지 */
    boolean existsByUserIdAndBadge_Id(Long userId, Long badgeId);
}
