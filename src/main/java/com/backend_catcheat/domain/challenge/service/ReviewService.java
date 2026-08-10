package com.backend_catcheat.domain.challenge.service;

import com.backend_catcheat.domain.auth.entity.User;
import com.backend_catcheat.domain.auth.repository.UserRepository;
import com.backend_catcheat.domain.challenge.dto.ReviewCreateResponseDTO;
import com.backend_catcheat.domain.challenge.dto.ReviewLikeResponseDTO;
import com.backend_catcheat.domain.challenge.dto.ReviewResponseDTO;
import com.backend_catcheat.domain.challenge.dto.ReviewWriteRequestDTO;
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final int CONTENT_MAX = 500;

    private final ReviewRepository reviewRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final ChallengeUnlockRepository unlockRepository;
    private final UserRepository userRepository;

    @Transactional
    public ReviewCreateResponseDTO writeFoodReview(Long userId, Long challengeDexId, Long slotId,
                                                   ReviewWriteRequestDTO request) {
        validate(request);
        ChallengeParticipant participant = participantRepository
                .findByChallengeDexIdAndUserId(challengeDexId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_NOT_JOINED));
        if (!unlockRepository.existsByChallengeParticipantIdAndSlotId(participant.getId(), slotId))
            throw new CustomException(ErrorCode.REVIEW_REQUIRES_UNLOCK);
        if (reviewRepository.existsByReviewerIdAndSlotId(userId, slotId))
            throw new CustomException(ErrorCode.REVIEW_ALREADY_EXISTS);

        Review saved = reviewRepository.save(
                Review.food(userId, challengeDexId, slotId, request.content(), request.rating()));
        return new ReviewCreateResponseDTO(saved.getId());
    }

    @Transactional
    public ReviewCreateResponseDTO writeChallengeReview(Long userId, Long challengeDexId,
                                                        ReviewWriteRequestDTO request) {
        validate(request);
        ChallengeParticipant participant = participantRepository
                .findByChallengeDexIdAndUserId(challengeDexId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_NOT_JOINED));
        if (!participant.isCompleted())
            throw new CustomException(ErrorCode.REVIEW_REQUIRES_COMPLETION);
        if (reviewRepository.existsByReviewerIdAndChallengeDexIdAndReviewType(
                userId, challengeDexId, ReviewType.CHALLENGE))
            throw new CustomException(ErrorCode.REVIEW_ALREADY_EXISTS);

        Review saved = reviewRepository.save(
                Review.challenge(userId, challengeDexId, request.content(), request.rating()));
        return new ReviewCreateResponseDTO(saved.getId());
    }

    @Transactional(readOnly = true)
    public List<ReviewResponseDTO> getFoodReviews(Long userId, Long challengeDexId, Long slotId) {
        return toResponses(reviewRepository.findBySlotIdOrderByLikeCountDescCreatedAtDesc(slotId), userId);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponseDTO> getChallengeReviews(Long userId, Long challengeDexId) {
        return toResponses(
                reviewRepository.findByChallengeDexIdAndReviewTypeOrderByLikeCountDescCreatedAtDesc(
                        challengeDexId, ReviewType.CHALLENGE),
                userId);
    }

    @Transactional
    public void editReview(Long userId, Long reviewId, ReviewWriteRequestDTO request) {
        validate(request);
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.REVIEW_NOT_FOUND));
        if (!review.isOwnedBy(userId)) throw new CustomException(ErrorCode.REVIEW_FORBIDDEN);
        review.edit(request.content(), request.rating());
    }

    @Transactional
    public void deleteReview(Long userId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.REVIEW_NOT_FOUND));
        if (!review.isOwnedBy(userId)) throw new CustomException(ErrorCode.REVIEW_FORBIDDEN);
        reviewLikeRepository.deleteByReviewId(reviewId);
        reviewRepository.delete(review);
    }

    @Transactional
    public ReviewLikeResponseDTO toggleLike(Long userId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.REVIEW_NOT_FOUND));
        var existing = reviewLikeRepository.findByReviewIdAndUserId(reviewId, userId);
        boolean liked;
        if (existing.isPresent()) {
            reviewLikeRepository.delete(existing.get());
            review.decreaseLike();
            liked = false;
        } else {
            reviewLikeRepository.save(ReviewLike.of(reviewId, userId));
            review.increaseLike();
            liked = true;
        }
        return new ReviewLikeResponseDTO(liked, review.getLikeCount());
    }

    private void validate(ReviewWriteRequestDTO request) {
        if (request.content() != null && request.content().length() > CONTENT_MAX)
            throw new CustomException(ErrorCode.REVIEW_CONTENT_TOO_LONG);
        if (request.rating() != null && (request.rating() < 1 || request.rating() > 5))
            throw new CustomException(ErrorCode.REVIEW_RATING_INVALID);
    }

    private List<ReviewResponseDTO> toResponses(List<Review> reviews, Long userId) {
        if (reviews.isEmpty()) return List.of();

        List<Long> reviewIds = reviews.stream().map(Review::getId).toList();
        List<Long> reviewerIds = reviews.stream().map(Review::getReviewerId).distinct().toList();

        Map<Long, String> nicknameById = userRepository.findAllById(reviewerIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));
        Set<Long> likedReviewIds = reviewLikeRepository.findByReviewIdInAndUserId(reviewIds, userId).stream()
                .map(ReviewLike::getReviewId)
                .collect(Collectors.toSet());

        return reviews.stream()
                .map(r -> new ReviewResponseDTO(
                        r.getId(),
                        r.getReviewerId(),
                        nicknameById.get(r.getReviewerId()),
                        r.getContent(),
                        r.getRating(),
                        r.getLikeCount(),
                        likedReviewIds.contains(r.getId()),
                        r.getReviewerId().equals(userId),
                        r.getCreatedAt(),
                        r.getUpdatedAt()))
                .toList();
    }
}
