package com.backend_catcheat.domain.onboarding.repository;

import com.backend_catcheat.domain.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

// 온보딩 상태 조회·변경 전용 리포지토리
public interface OnboardingRepository extends JpaRepository<User, Long> {
}
