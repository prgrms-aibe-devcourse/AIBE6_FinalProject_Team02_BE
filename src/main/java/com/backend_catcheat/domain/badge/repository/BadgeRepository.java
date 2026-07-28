package com.backend_catcheat.domain.badge.repository;

import com.backend_catcheat.domain.badge.entity.Badge;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BadgeRepository extends JpaRepository<Badge, Long> {
}
