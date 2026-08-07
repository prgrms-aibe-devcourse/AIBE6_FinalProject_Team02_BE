package com.backend_catcheat.domain.challenge.repository;

import com.backend_catcheat.domain.challenge.entity.Review;
import com.backend_catcheat.domain.challenge.entity.ReviewType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByReviewerIdAndSlotId(Long reviewerId, Long slotId);
    boolean existsByReviewerIdAndChallengeDexIdAndReviewType(
            Long reviewerId, Long challengeDexId, ReviewType type);
    List<Review> findBySlotIdOrderByLikeCountDescCreatedAtDesc(Long slotId);
    List<Review> findByChallengeDexIdAndReviewTypeOrderByLikeCountDescCreatedAtDesc(
            Long challengeDexId, ReviewType type);
}
