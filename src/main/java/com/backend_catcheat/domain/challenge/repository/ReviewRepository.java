package com.backend_catcheat.domain.challenge.repository;

import com.backend_catcheat.domain.challenge.entity.Review;
import com.backend_catcheat.domain.challenge.entity.ReviewType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByReviewerIdAndSlotId(Long reviewerId, Long slotId);
    boolean existsByReviewerIdAndChallengeDexIdAndReviewType(
            Long reviewerId, Long challengeDexId, ReviewType type);
    List<Review> findBySlotIdOrderByLikeCountDescCreatedAtDesc(Long slotId);
    List<Review> findByChallengeDexIdAndReviewTypeOrderByLikeCountDescCreatedAtDesc(
            Long challengeDexId, ReviewType type);

    // 내가 쓴 리뷰
    List<Review> findByReviewerIdOrderByCreatedAtDesc(Long reviewerId);

    // 좋아요 수는 순수 카운터 → DB가 직접 계산하는 원자적 UPDATE로 lost update 방지
    @Modifying
    @Query("update Review r set r.likeCount = r.likeCount + 1 where r.id = :id")
    void incrementLikeCount(@Param("id") Long id);

    @Modifying
    @Query("update Review r set r.likeCount = r.likeCount - 1 where r.id = :id and r.likeCount > 0")
    void decrementLikeCount(@Param("id") Long id);
}
