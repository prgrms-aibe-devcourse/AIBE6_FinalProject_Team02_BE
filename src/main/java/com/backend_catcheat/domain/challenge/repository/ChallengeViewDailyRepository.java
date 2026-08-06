package com.backend_catcheat.domain.challenge.repository;

import com.backend_catcheat.domain.challenge.entity.ChallengeViewDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ChallengeViewDailyRepository extends JpaRepository<ChallengeViewDaily, Long> {

    // 상세 진입 시 그날 행 +1 (없으면 생성)
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO challenge_view_daily (challenge_dex_id, view_date, view_count)
            VALUES (:dexId, CURRENT_DATE, 1)
            ON CONFLICT (challenge_dex_id, view_date)
            DO UPDATE SET view_count = challenge_view_daily.view_count + 1
            """)
    void increment(@Param("dexId") Long dexId);

    // 최근 N일 조회수 합(랭킹)
    @Query("""
            select v.challengeDexId as dexId, sum(v.viewCount) as score
            from ChallengeViewDaily v
            where v.challengeDexId in :dexIds and v.viewDate >= :sinceDate
            group by v.challengeDexId
            """)
    List<DexScore> sumRecentViewsByDexIn(@Param("dexIds") List<Long> dexIds,
                                         @Param("sinceDate") LocalDate sinceDate);
}
