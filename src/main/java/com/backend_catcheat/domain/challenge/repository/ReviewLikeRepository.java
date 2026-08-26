package com.backend_catcheat.domain.challenge.repository;

import com.backend_catcheat.domain.challenge.entity.ReviewLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewLikeRepository extends JpaRepository<ReviewLike, Long> {
    Optional<ReviewLike> findByReviewIdAndUserId(Long reviewId, Long userId);

    // 좋아요 실제 개수(소스 오브 트루스)
    long countByReviewId(Long reviewId);
    void deleteByReviewId(Long reviewId);
    List<ReviewLike> findByReviewIdInAndUserId(List<Long> reviewIds, Long userId);

    // 내가 좋아요한 리뷰
    List<ReviewLike> findByUserIdOrderByCreatedAtDesc(Long userId);
}
