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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "사용자 · 공개 프로필 · 내 활동", description = """
        닉네임 검색, 남의 공개 프로필, 그리고 **내 활동 내역**(내가 쓴 리뷰·댓글, 내가 좋아요한 것)을 모은다.

        마이페이지가 여러 도메인에 걸쳐 있어 여기서 챌린짓·로그잇 활동을 함께 읽는다.
        """)
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
    @Operation(summary = "닉네임으로 사용자 검색", description = "친구 추가 화면에서 쓴다. 탈퇴한 사용자는 나오지 않는다.")
    @GetMapping("/search")
    public ApiResponse<List<UserSearchResultDTO>> search(
            @AuthenticationPrincipal Long userId,
            @RequestParam String nickname
    ) {
        return ApiResponse.ok(userProfileService.search(userId, nickname));
    }

    /** 내가 쓴 리뷰-최신순, 삭제된 챌린짓 것은 제외 */
    @Operation(summary = "내가 쓴 리뷰", description = "최신순. **삭제된 챌린지의 리뷰는 제외**된다.")
    @GetMapping("/me/reviews")
    public ApiResponse<List<MyReviewResponseDTO>> myReviews(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.ok(reviewService.getMyReviews(userId));
    }

    /** 내가 좋아요한 리뷰-내가 누른 순, 챌린짓·음식 리뷰 모두 */
    @Operation(summary = "내가 좋아요한 리뷰", description = "**내가 누른 순.** 챌린지 리뷰와 음식 리뷰를 함께 돌려준다.")
    @GetMapping("/me/liked-reviews")
    public ApiResponse<List<LikedReviewResponseDTO>> likedReviews(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.ok(reviewService.getLikedReviews(userId));
    }

    /** 내가 쓴 로그잇 댓글-최신순 */
    @Operation(summary = "내가 쓴 로그잇 댓글", description = "최신순.")
    @GetMapping("/me/logit-comments")
    public ApiResponse<List<MyLogitCommentResponseDTO>> myLogitComments(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.ok(madeDexActivityService.getMyComments(userId));
    }

    /** 내가 좋아요한 로그잇 기록-내가 누른 순 */
    @Operation(summary = "내가 좋아요한 로그잇 기록", description = "**내가 누른 순.**")
    @GetMapping("/me/liked-logit-records")
    public ApiResponse<List<LikedLogitRecordResponseDTO>> likedLogitRecords(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.ok(madeDexActivityService.getLikedRecords(userId));
    }

    /** 내 공개 프로필 미리보기 */
    @Operation(summary = "내 공개 프로필 미리보기", description = "남에게 어떻게 보이는지 그대로 확인한다.")
    @GetMapping("/me/profile")
    public ApiResponse<PublicProfileDTO> myProfile(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.ok(userProfileService.getProfile(userId, userId));
    }

    /** 다른 사람 공개 프로필 */
    @Operation(summary = "다른 사람 공개 프로필", description = "닉네임·대표 뱃지·친구 관계 상태를 함께 돌려준다.")
    @GetMapping("/{targetUserId}/profile")
    public ApiResponse<PublicProfileDTO> profile(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long targetUserId
    ) {
        return ApiResponse.ok(userProfileService.getProfile(userId, targetUserId));
    }

    /** 다른 사람 기본도감 */
    @Operation(summary = "다른 사람 기본 도감", description = "공개용이라 남의 200칸 수집 현황만 내려간다.")
    @GetMapping("/{targetUserId}/basic-dex")
    public ApiResponse<List<MyBasicDexResponseDTO>> basicDex(
            @PathVariable Long targetUserId
    ) {
        return ApiResponse.ok(basicDexService.findPublicBasicDex(targetUserId));
    }

    /** 다른 사람 챌린지도감 */
    @Operation(summary = "다른 사람 챌린지 도감", description = "`relation` 으로 개설한 것 · 참여 중 · 완료한 것을 나눠 본다.")
    @GetMapping("/{targetUserId}/challenges")
    public ApiResponse<List<ChallengeSummaryDTO>> challenges(
            @PathVariable Long targetUserId,
            @RequestParam MyChallengeRelation relation
    ) {
        return ApiResponse.ok(challengeService.getMyChallenges(targetUserId, relation));
    }
}
