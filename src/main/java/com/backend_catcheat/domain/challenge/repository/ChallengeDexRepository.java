package com.backend_catcheat.domain.challenge.repository;

import com.backend_catcheat.domain.challenge.entity.ChallengeDex;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChallengeDexRepository extends JpaRepository<ChallengeDex, Long> {
    Optional<ChallengeDex> findByIdAndDeletedAtIsNull(Long id);
}
