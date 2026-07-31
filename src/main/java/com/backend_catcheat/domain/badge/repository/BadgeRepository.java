package com.backend_catcheat.domain.badge.repository;

import com.backend_catcheat.domain.badge.entity.Badge;
import com.backend_catcheat.domain.badge.entity.type.BadgeConditionType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BadgeRepository extends JpaRepository<Badge, Long> {

    /** 지급 조건 유형별 뱃지 */
    List<Badge> findByConditionType(BadgeConditionType conditionType);
}
