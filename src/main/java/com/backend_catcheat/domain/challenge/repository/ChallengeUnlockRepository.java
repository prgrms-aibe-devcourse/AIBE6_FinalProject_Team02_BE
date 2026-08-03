package com.backend_catcheat.domain.challenge.repository;

import com.backend_catcheat.domain.challenge.entity.ChallengeUnlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChallengeUnlockRepository extends JpaRepository<ChallengeUnlock, Long> {
    List<ChallengeUnlock> findByChallengeParticipantId(Long participantId);
    long countByChallengeParticipantId(Long participantId);   // 해금 개수(완료판정·랭킹)
    boolean existsByChallengeParticipantIdAndSlotId(Long participantId, Long slotId);
}
