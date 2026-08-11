package com.backend_catcheat.domain.user.controller;

import com.backend_catcheat.domain.challenge.entity.MyChallengeRelation;
import com.backend_catcheat.domain.challenge.dto.ChallengeSummaryDTO;
import com.backend_catcheat.domain.challenge.service.ChallengeService;
import com.backend_catcheat.domain.dex.basicdex.service.BasicDexService;
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

    /** 닉네임 검색 */
    @GetMapping("/search")
    public ApiResponse<List<UserSearchResultDTO>> search(
            @AuthenticationPrincipal Long userId,
            @RequestParam String nickname
    ) {
        return ApiResponse.ok(userProfileService.search(userId, nickname));
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
