package com.backend_catcheat.domain.user.controller;

import com.backend_catcheat.domain.challenge.entity.MyChallengeRelation;
import com.backend_catcheat.domain.challenge.dto.ChallengeSummaryDTO;
import com.backend_catcheat.domain.challenge.dto.LikedReviewResponseDTO;
import com.backend_catcheat.domain.challenge.dto.MyReviewResponseDTO;
import com.backend_catcheat.domain.challenge.service.ChallengeService;
import com.backend_catcheat.domain.challenge.service.ReviewService;
import com.backend_catcheat.domain.dex.basicdex.service.BasicDexService;
import com.backend_catcheat.domain.made.dto.LikedLogitRecordResponseDTO;
import com.backend_catcheat.domain.made.dto.MyLogitCommentResponseDTO;
import com.backend_catcheat.domain.made.service.MadeDexActivityService;
import com.backend_catcheat.domain.my.dto.MyBasicDexResponseDTO;
import com.backend_catcheat.domain.user.dto.PublicProfileDTO;
import com.backend_catcheat.domain.user.dto.UserSearchResultDTO;
import com.backend_catcheat.domain.user.service.UserProfileService;
import com.backend_catcheat.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserProfileService userProfileService;
    private final BasicDexService basicDexService;
    private final ChallengeService challengeService;
    private final ReviewService reviewService;
    private final MadeDexActivityService madeDexActivityService;

    /** 닉네임 검색 */
    @GetMapping("/search")
    public ApiResponse<List<UserSearchResultDTO>> search(
            @AuthenticationPrincipal Long userId,
            @RequestParam String nickname
    ) {
        return ApiResponse.ok(userProfileService.search(userId, nickname));
    }

    /** 내가 쓴 리뷰-최신순, 삭제된 챌린짓 것은 제외 */
    @GetMapping("/me/reviews")
    public ApiResponse<List<MyReviewResponseDTO>> myReviews(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.ok(reviewService.getMyReviews(userId));
    }

    /** 내가 좋아요한 리뷰-내가 누른 순, 챌린짓·음식 리뷰 모두 */
    @GetMapping("/me/liked-reviews")
    public ApiResponse<List<LikedReviewResponseDTO>> likedReviews(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.ok(reviewService.getLikedReviews(userId));
    }

    /** 내가 쓴 로그잇 댓글-최신순 */
    @GetMapping("/me/logit-comments")
    public ApiResponse<List<MyLogitCommentResponseDTO>> myLogitComments(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.ok(madeDexActivityService.getMyComments(userId));
    }

    /** 내가 좋아요한 로그잇 기록-내가 누른 순 */
    @GetMapping("/me/liked-logit-records")
    public ApiResponse<List<LikedLogitRecordResponseDTO>> likedLogitRecords(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.ok(madeDexActivityService.getLikedRecords(userId));
    }

    /** 내 공개 프로필 미리보기 */
    @GetMapping("/me/profile")
    public ApiResponse<PublicProfileDTO> myProfile(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.ok(userProfileService.getProfile(userId, userId));
    }

    /** 다른 사람 공개 프로필 */
    @GetMapping("/{targetUserId}/profile")
    public ApiResponse<PublicProfileDTO> profile(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long targetUserId
    ) {
        return ApiResponse.ok(userProfileService.getProfile(userId, targetUserId));
    }

    /** 다른 사람 기본도감 */
    @GetMapping("/{targetUserId}/basic-dex")
    public ApiResponse<List<MyBasicDexResponseDTO>> basicDex(
            @PathVariable Long targetUserId
    ) {
        return ApiResponse.ok(basicDexService.findPublicBasicDex(targetUserId));
    }

    /** 다른 사람 챌린지도감 */
    @GetMapping("/{targetUserId}/challenges")
    public ApiResponse<List<ChallengeSummaryDTO>> challenges(
            @PathVariable Long targetUserId,
            @RequestParam MyChallengeRelation relation
    ) {
        return ApiResponse.ok(challengeService.getMyChallenges(targetUserId, relation));
    }
}
