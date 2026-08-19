package com.backend_catcheat.domain.made.repository;

import com.backend_catcheat.domain.made.entity.MadeDexRecordLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MadeDexRecordLikeRepository extends JpaRepository<MadeDexRecordLike, Long> {
    Optional<MadeDexRecordLike> findByRecordIdAndUserId(Long recordId, Long userId);
}
