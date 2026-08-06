package com.backend_catcheat.domain.challenge.repository;

import com.backend_catcheat.domain.challenge.entity.ChallengeDexSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChallengeDexSlotRepository extends JpaRepository<ChallengeDexSlot, Long> {
    List<ChallengeDexSlot> findByChallengeDexIdOrderBySlotOrderAsc(Long challengeDexId);
    long countByChallengeDexId(Long challengeDexId);
}
