package com.backend_catcheat.domain.challenge.controller;

import com.backend_catcheat.domain.challenge.dto.ReviewCreateResponseDTO;
import com.backend_catcheat.domain.challenge.dto.ReviewLikeResponseDTO;
import com.backend_catcheat.domain.challenge.dto.ReviewResponseDTO;
import com.backend_catcheat.domain.challenge.dto.ReviewWriteRequestDTO;
import com.backend_catcheat.domain.challenge.service.ReviewService;
import com.backend_catcheat.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/challenges")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    //음식 리뷰 작성 (해당 슬롯 해금 후)
    @PostMapping("/{challengeId}/slots/{slotId}/reviews")
    public ApiResponse<ReviewCreateResponseDTO> writeFoodReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long challengeId,
            @PathVariable Long slotId,
            @RequestBody ReviewWriteRequestDTO request) {
        return ApiResponse.ok(reviewService.writeFoodReview(userId, challengeId, slotId, request));
    }

    //챌린지 리뷰 작성 (챌린지 완료 후)
    @PostMapping("/{challengeId}/reviews")
    public ApiResponse<ReviewCreateResponseDTO> writeChallengeReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long challengeId,
            @RequestBody ReviewWriteRequestDTO request) {
        return ApiResponse.ok(reviewService.writeChallengeReview(userId, challengeId, request));
    }

    //음식 리뷰 목록
    @GetMapping("/{challengeId}/slots/{slotId}/reviews")
    public ApiResponse<List<ReviewResponseDTO>> foodReviews(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long challengeId,
            @PathVariable Long slotId) {
        return ApiResponse.ok(reviewService.getFoodReviews(userId, challengeId, slotId));
    }

    //챌린지 리뷰 목록
    @GetMapping("/{challengeId}/reviews")
    public ApiResponse<List<ReviewResponseDTO>> challengeReviews(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long challengeId) {
        return ApiResponse.ok(reviewService.getChallengeReviews(userId, challengeId));
    }

    //리뷰 수정 (작성자 본인)
    @PatchMapping("/reviews/{reviewId}")
    public ApiResponse<Void> editReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long reviewId,
            @RequestBody ReviewWriteRequestDTO request) {
        reviewService.editReview(userId, reviewId, request);
        return ApiResponse.ok();
    }

    //리뷰 삭제 (작성자 본인)
    @DeleteMapping("/reviews/{reviewId}")
    public ApiResponse<Void> deleteReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long reviewId) {
        reviewService.deleteReview(userId, reviewId);
        return ApiResponse.ok();
    }

    //좋아요 토글 (중복 방지)
    @PostMapping("/reviews/{reviewId}/likes")
    public ApiResponse<ReviewLikeResponseDTO> toggleLike(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long reviewId) {
        return ApiResponse.ok(reviewService.toggleLike(userId, reviewId));
    }
}
