package com.backend_catcheat.domain.challenge.repository;

import com.backend_catcheat.domain.challenge.entity.ChallengeDexSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChallengeDexSlotRepository extends JpaRepository<ChallengeDexSlot, Long> {
    List<ChallengeDexSlot> findByChallengeDexIdOrderBySlotOrderAsc(Long challengeDexId);
    long countByChallengeDexId(Long challengeDexId);

    // 챌린지 삭제 전 S3 정리용 — 이 챌린지 슬롯들의 목표 사진 key
    @Query("select s.imageKey from ChallengeDexSlot s where s.challengeDexId = :dexId and s.imageKey is not null")
    List<String> findImageKeysByChallengeDexId(@Param("dexId") Long dexId);

    // 목록 화면 N+1 방지 — 여러 챌린지의 슬롯 수를 한 번에 집계
    @Query("select s.challengeDexId as dexId, count(s) as cnt " +
            "from ChallengeDexSlot s where s.challengeDexId in :dexIds " +
            "group by s.challengeDexId")
    List<SlotCount> countByChallengeDexIdIn(@Param("dexIds") List<Long> dexIds);

    interface SlotCount {
        Long getDexId();
        long getCnt();
    }
}
