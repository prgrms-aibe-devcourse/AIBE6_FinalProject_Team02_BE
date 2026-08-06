package com.backend_catcheat.domain.challenge.controller;

import com.backend_catcheat.domain.challenge.dto.PresetBadgeDTO;
import com.backend_catcheat.domain.challenge.dto.RewardBadgeDTO;
import com.backend_catcheat.domain.challenge.dto.RewardBadgeCreateRequestDTO;
import com.backend_catcheat.domain.challenge.dto.RewardBadgeCreateResponseDTO;
import com.backend_catcheat.domain.challenge.service.ChallengeRewardBadgeService;
import com.backend_catcheat.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 챌린지 보상 뱃지 — 개설 전 프리셋 조회 / 보상 뱃지 생성
 */
@RestController
@RequestMapping("/api/v1/challenges/reward-badges")
@RequiredArgsConstructor
public class ChallengeRewardBadgeController {
    private final ChallengeRewardBadgeService challengeRewardBadgeService;

    //프리셋 목록
    @GetMapping("/presets")
    public ApiResponse<List<PresetBadgeDTO>> presets() {
        return ApiResponse.ok(challengeRewardBadgeService.getPresets());
    }

    //보상 뱃지 표시 정보(상세 미리보기·완료 팝업)
    @GetMapping("/{badgeId}")
    public ApiResponse<RewardBadgeDTO> rewardBadge(@PathVariable Long badgeId) {
        return ApiResponse.ok(challengeRewardBadgeService.getRewardBadge(badgeId));
    }

    //보상 뱃지 생성 → 반환된 badgeId를 개설 요청 rewardBadgeId로 사용
    @PostMapping
    public ApiResponse<RewardBadgeCreateResponseDTO> create(
            @AuthenticationPrincipal Long userId,
            @RequestBody RewardBadgeCreateRequestDTO request
    ) {
        return ApiResponse.ok(challengeRewardBadgeService.create(request));
    }
}
