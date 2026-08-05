package com.backend_catcheat.domain.challenge.repository;

import com.backend_catcheat.domain.challenge.entity.ChallengeParticipant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChallengeParticipantRepository extends JpaRepository<ChallengeParticipant, Long> {
    Optional<ChallengeParticipant> findByChallengeDexIdAndUserId(Long challengeDexId, Long userId);
    // 내 참여 목록 (참여 중 / 완료)
    List<ChallengeParticipant> findByUserIdAndCompletedAtIsNull(Long userId);
    List<ChallengeParticipant> findByUserIdAndCompletedAtIsNotNull(Long userId);
    // 해금 완료 판정 동시성 — 참여자 행을 잠가 마지막 슬롯 동시 해금 시 완료 누락 방지
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ChallengeParticipant p " +
            "where p.challengeDexId = :challengeDexId and p.userId = :userId")
    Optional<ChallengeParticipant> findForUpdateByChallengeDexIdAndUserId(
            @Param("challengeDexId") Long challengeDexId, @Param("userId") Long userId);
    boolean existsByChallengeDexIdAndUserId(Long challengeDexId, Long userId);
    long countByChallengeDexId(Long challengeDexId);   // 참여자수(랭킹)

    // 목록 화면 N+1 방지 — 여러 챌린지의 참여자 수를 한 번에 집계
    @Query("select p.challengeDexId as dexId, count(p) as cnt " +
            "from ChallengeParticipant p " +
            "where p.challengeDexId in :dexIds " +
            "group by p.challengeDexId")
    List<ParticipantCount> countByChallengeDexIdIn(@Param("dexIds") List<Long> dexIds);

    interface ParticipantCount {
        Long getDexId();
        long getCnt();
    }

    // 최근 7일 신규 참여 수(랭킹)
    @Query("""
            select p.challengeDexId as dexId, count(p) as score
            from ChallengeParticipant p
            where p.challengeDexId in :dexIds and p.joinedAt >= :since
            group by p.challengeDexId
            """)
    List<DexScore> countRecentJoinsByDexIn(@Param("dexIds") List<Long> dexIds,
                                           @Param("since") LocalDateTime since);

    // 목록에서 내 참여 여부 표시용 — 유저가 참여한 dex id만
    @Query("select p.challengeDexId from ChallengeParticipant p " +
            "where p.userId = :userId and p.challengeDexId in :dexIds")
    List<Long> findJoinedDexIds(@Param("userId") Long userId, @Param("dexIds") List<Long> dexIds);
}
