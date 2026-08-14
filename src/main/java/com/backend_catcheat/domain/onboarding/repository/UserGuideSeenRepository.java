package com.backend_catcheat.domain.onboarding.repository;

import com.backend_catcheat.domain.onboarding.entity.UserGuideSeen;
import com.backend_catcheat.domain.onboarding.entity.UserGuideSeenId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserGuideSeenRepository extends JpaRepository<UserGuideSeen, UserGuideSeenId> {

    /** 이 사용자가 본 가이드 키 목록 */
    @Query("select g.guideKey from UserGuideSeen g where g.userId = :userId")
    List<String> findKeysByUserId(@Param("userId") Long userId);

    /** 이미 있으면 아무것도 하지 않고 없으면 넣음 (원자적) */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO user_guide_seen (user_id, guide_key)
            VALUES (:userId, :guideKey)
            ON CONFLICT (user_id, guide_key) DO NOTHING
            """)
    void insertIfAbsent(@Param("userId") Long userId, @Param("guideKey") String guideKey);
}
