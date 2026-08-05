package com.backend_catcheat.domain.challenge.repository;

import com.backend_catcheat.domain.challenge.entity.ChallengeUnlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChallengeUnlockRepository extends JpaRepository<ChallengeUnlock, Long> {
    List<ChallengeUnlock> findByChallengeParticipantId(Long participantId);
    long countByChallengeParticipantId(Long participantId);   // 해금 개수(완료판정·랭킹)
    boolean existsByChallengeParticipantIdAndSlotId(Long participantId, Long slotId);

    // 챌린지 포기 시 내 인증 기록 일괄 삭제
    void deleteByChallengeParticipantId(Long participantId);

    // 최근 7일 해금 수(랭킹)
    @Query(nativeQuery = true, value = """
            select p.challenge_dex_id as dexId, count(*) as score
            from challenge_unlock u
            join challenge_participant p on u.challenge_participant_id = p.id
            where p.challenge_dex_id in (:dexIds) and u.unlocked_at >= :since
            group by p.challenge_dex_id
            """)
    List<DexScore> countRecentUnlocksByDexIn(@Param("dexIds") List<Long> dexIds,
                                             @Param("since") LocalDateTime since);
}
