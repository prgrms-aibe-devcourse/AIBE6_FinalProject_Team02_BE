package com.backend_catcheat.domain.illustration.repository;

import com.backend_catcheat.domain.illustration.entity.IllustrationJob;
import com.backend_catcheat.domain.illustration.entity.IllustrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface IllustrationJobRepository extends JpaRepository<IllustrationJob, Long> {

    long countByUserIdAndCreatedAtGreaterThanEqual(Long userId, LocalDateTime from);

    List<IllustrationJob> findByStatusAndCreatedAtLessThan(IllustrationStatus status, LocalDateTime before);
}
