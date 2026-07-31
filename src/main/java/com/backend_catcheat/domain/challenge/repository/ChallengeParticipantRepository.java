package com.backend_catcheat.domain.challenge.repository;

import com.backend_catcheat.domain.challenge.entity.ChallengeParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChallengeParticipantRepository extends JpaRepository<ChallengeParticipant, Long> {
    Optional<ChallengeParticipant> findByChallengeDexIdAndUserId(Long challengeDexId, Long userId);
    boolean existsByChallengeDexIdAndUserId(Long challengeDexId, Long userId);
    long countByChallengeDexId(Long challengeDexId);   // 참여자수(랭킹)
}
