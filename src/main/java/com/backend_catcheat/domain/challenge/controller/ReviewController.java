package com.backend_catcheat.domain.challenge.controller;

import com.backend_catcheat.domain.challenge.dto.ReviewCreateResponseDTO;
import com.backend_catcheat.domain.challenge.dto.ReviewLikeResponseDTO;
import com.backend_catcheat.domain.challenge.dto.ReviewResponseDTO;
import com.backend_catcheat.domain.challenge.dto.ReviewWriteRequestDTO;
import com.backend_catcheat.domain.challenge.service.ReviewService;
import com.backend_catcheat.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "챌린짓 · 리뷰", description = """
        리뷰는 **먹어 본 사람만** 쓴다. 음식 리뷰는 그 슬롯을 해금한 뒤, 챌린지 리뷰는 완주한 뒤에 열린다.
        수정·삭제는 작성자 본인만 가능하고, 좋아요는 한 사람이 한 번만 눌린다.
        """)
@RestController
@RequestMapping("/api/v1/challenges")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    //음식 리뷰 작성 (해당 슬롯 해금 후)
    @Operation(summary = "음식 리뷰 작성", description = "**해당 슬롯을 해금한 뒤에만** 쓸 수 있다.")
    @PostMapping("/{challengeId}/slots/{slotId}/reviews")
    public ApiResponse<ReviewCreateResponseDTO> writeFoodReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long challengeId,
            @PathVariable Long slotId,
            @RequestBody ReviewWriteRequestDTO request) {
        return ApiResponse.ok(reviewService.writeFoodReview(userId, challengeId, slotId, request));
    }

    //챌린지 리뷰 작성 (챌린지 완료 후)
    @Operation(summary = "챌린지 리뷰 작성", description = "**챌린지를 완주한 뒤에만** 쓸 수 있다.")
    @PostMapping("/{challengeId}/reviews")
    public ApiResponse<ReviewCreateResponseDTO> writeChallengeReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long challengeId,
            @RequestBody ReviewWriteRequestDTO request) {
        return ApiResponse.ok(reviewService.writeChallengeReview(userId, challengeId, request));
    }

    //음식 리뷰 목록
    @Operation(summary = "음식 리뷰 목록", description = "슬롯 하나에 달린 리뷰를 좋아요 수·내가 눌렀는지와 함께 돌려준다.")
    @GetMapping("/{challengeId}/slots/{slotId}/reviews")
    public ApiResponse<List<ReviewResponseDTO>> foodReviews(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long challengeId,
            @PathVariable Long slotId) {
        return ApiResponse.ok(reviewService.getFoodReviews(userId, challengeId, slotId));
    }

    //챌린지 리뷰 목록
    @Operation(summary = "챌린지 리뷰 목록", description = "완주자들이 남긴 챌린지 전체 리뷰.")
    @GetMapping("/{challengeId}/reviews")
    public ApiResponse<List<ReviewResponseDTO>> challengeReviews(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long challengeId) {
        return ApiResponse.ok(reviewService.getChallengeReviews(userId, challengeId));
    }

    //리뷰 수정 (작성자 본인)
    @Operation(summary = "리뷰 수정", description = "작성자 본인만 가능.")
    @PatchMapping("/reviews/{reviewId}")
    public ApiResponse<Void> editReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long reviewId,
            @RequestBody ReviewWriteRequestDTO request) {
        reviewService.editReview(userId, reviewId, request);
        return ApiResponse.ok();
    }

    //리뷰 삭제 (작성자 본인)
    @Operation(summary = "리뷰 삭제", description = "작성자 본인만 가능.")
    @DeleteMapping("/reviews/{reviewId}")
    public ApiResponse<Void> deleteReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long reviewId) {
        reviewService.deleteReview(userId, reviewId);
        return ApiResponse.ok();
    }

    //좋아요 토글 (중복 방지)
    @Operation(summary = "리뷰 좋아요 토글", description = "한 사람이 한 번만 눌린다. 다시 누르면 취소된다.")
    @PostMapping("/reviews/{reviewId}/likes")
    public ApiResponse<ReviewLikeResponseDTO> toggleLike(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long reviewId) {
        return ApiResponse.ok(reviewService.toggleLike(userId, reviewId));
    }
}
