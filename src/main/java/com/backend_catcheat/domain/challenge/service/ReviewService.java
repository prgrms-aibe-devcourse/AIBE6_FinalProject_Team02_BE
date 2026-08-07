package com.backend_catcheat.domain.challenge.service;

import com.backend_catcheat.domain.challenge.entity.ChallengeParticipant;
import com.backend_catcheat.domain.challenge.entity.Review;
import com.backend_catcheat.domain.challenge.entity.ReviewLike;
import com.backend_catcheat.domain.challenge.entity.ReviewType;
import com.backend_catcheat.domain.challenge.repository.ChallengeParticipantRepository;
import com.backend_catcheat.domain.challenge.repository.ChallengeUnlockRepository;
import com.backend_catcheat.domain.challenge.repository.ReviewLikeRepository;
import com.backend_catcheat.domain.challenge.repository.ReviewRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final ChallengeUnlockRepository unlockRepository;

    /** 음식 리뷰 — 해당 슬롯을 "해금"해야만 가능 */
    @Transactional
    public Long writeFoodReview(Long userId, Long challengeDexId, Long slotId,
                                String content, Integer rating) {
        // 1) 이 챌린지 참여자인지
        ChallengeParticipant participant = participantRepository
                .findByChallengeDexIdAndUserId(challengeDexId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_NOT_JOINED));
        // 2) 이 슬롯을 해금했는지
        boolean unlocked = unlockRepository
                .existsByChallengeParticipantIdAndSlotId(participant.getId(), slotId);
        if (!unlocked) throw new CustomException(ErrorCode.REVIEW_REQUIRES_UNLOCK);
        // 3) 중복 방지 (한 슬롯에 1개)
        if (reviewRepository.existsByReviewerIdAndSlotId(userId, slotId))
            throw new CustomException(ErrorCode.REVIEW_ALREADY_EXISTS);

        return reviewRepository.save(
                Review.food(userId, challengeDexId, slotId, content, rating)).getId();
    }

    /** 챌린지 리뷰 — 챌린지를 "완료"해야만 가능 */
    @Transactional
    public Long writeChallengeReview(Long userId, Long challengeDexId,
                                     String content, Integer rating) {
        ChallengeParticipant participant = participantRepository
                .findByChallengeDexIdAndUserId(challengeDexId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_NOT_JOINED));
        // 완료 조건
        if (!participant.isCompleted())
            throw new CustomException(ErrorCode.REVIEW_REQUIRES_COMPLETION);
        // 중복 방지
        if (reviewRepository.existsByReviewerIdAndChallengeDexIdAndReviewType(
                userId, challengeDexId, ReviewType.CHALLENGE))
            throw new CustomException(ErrorCode.REVIEW_ALREADY_EXISTS);

        return reviewRepository.save(
                Review.challenge(userId, challengeDexId, content, rating)).getId();
    }

    /** 수정 — 작성자 본인만 */
    @Transactional
    public void editReview(Long userId, Long reviewId, String content, Integer rating) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.REVIEW_NOT_FOUND));
        if (!review.isOwnedBy(userId)) throw new CustomException(ErrorCode.REVIEW_FORBIDDEN);
        review.edit(content, rating);
    }

    /** 삭제 — 작성자 본인만 */
    @Transactional
    public void deleteReview(Long userId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.REVIEW_NOT_FOUND));
        if (!review.isOwnedBy(userId)) throw new CustomException(ErrorCode.REVIEW_FORBIDDEN);
        reviewLikeRepository.deleteByReviewId(reviewId);   // 좋아요 함께 정리
        reviewRepository.delete(review);
    }

    /** 좋아요 토글 — 이미 눌렀으면 취소, 아니면 추가 (중복은 유니크로도 이중 방지) */
    @Transactional
    public boolean toggleLike(Long userId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.REVIEW_NOT_FOUND));
        var existing = reviewLikeRepository.findByReviewIdAndUserId(reviewId, userId);
        if (existing.isPresent()) {
            reviewLikeRepository.delete(existing.get());
            review.decreaseLike();
            return false;   // 좋아요 취소됨
        }
        reviewLikeRepository.save(ReviewLike.of(reviewId, userId));
        review.increaseLike();
        return true;        // 좋아요 됨
    }
}