package com.backend_catcheat.domain.challenge.controller;

import com.backend_catcheat.domain.challenge.dto.PresetBadgeDTO;
import com.backend_catcheat.domain.challenge.dto.RewardBadgeDTO;
import com.backend_catcheat.domain.challenge.dto.RewardBadgeCreateRequestDTO;
import com.backend_catcheat.domain.challenge.dto.RewardBadgeCreateResponseDTO;
import com.backend_catcheat.domain.challenge.service.ChallengeRewardBadgeService;
import com.backend_catcheat.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 챌린지 보상 뱃지 — 개설 전 프리셋 조회 / 보상 뱃지 생성
 */
@Tag(name = "챌린짓 · 보상 뱃지", description = """
        완주자에게 줄 뱃지. 운영진 프리셋으로 고정하지 않고 **개설자가 직접 만들 수 있게** 열어 뒀다 —
        시즌 미션이 개설자의 것이라 보상도 개설자의 것이어야 참여 동기가 선다.

        개설 흐름은 **뱃지 생성 → 반환된 `badgeId` 를 챌린지 개설 요청에 싣기** 순서다.
        """)
@RestController
@RequestMapping("/api/v1/challenges/reward-badges")
@RequiredArgsConstructor
public class ChallengeRewardBadgeController {
    private final ChallengeRewardBadgeService challengeRewardBadgeService;

    //프리셋 목록
    @Operation(summary = "프리셋 뱃지 목록", description = "직접 만들지 않고 고를 수 있는 기본 뱃지 목록.")
    @GetMapping("/presets")
    public ApiResponse<List<PresetBadgeDTO>> presets() {
        return ApiResponse.ok(challengeRewardBadgeService.getPresets());
    }

    //보상 뱃지 표시 정보(상세 미리보기·완료 팝업)
    @Operation(summary = "보상 뱃지 조회", description = "챌린지 상세 미리보기와 완주 팝업에서 쓰는 표시 정보.")
    @GetMapping("/{badgeId}")
    public ApiResponse<RewardBadgeDTO> rewardBadge(@PathVariable Long badgeId) {
        return ApiResponse.ok(challengeRewardBadgeService.getRewardBadge(badgeId));
    }

    //보상 뱃지 생성 → 반환된 badgeId를 개설 요청 rewardBadgeId로 사용
    @Operation(summary = "보상 뱃지 생성", description = "반환된 `badgeId` 를 챌린지 개설 요청의 `rewardBadgeId` 로 넘긴다.")
    @PostMapping
    public ApiResponse<RewardBadgeCreateResponseDTO> create(
            @AuthenticationPrincipal Long userId,
            @RequestBody RewardBadgeCreateRequestDTO request
    ) {
        return ApiResponse.ok(challengeRewardBadgeService.create(request));
    }
}
