package com.backend_catcheat.domain.challenge.service;

import com.backend_catcheat.domain.badge.entity.Badge;
import com.backend_catcheat.domain.badge.entity.type.BadgeConditionType;
import com.backend_catcheat.domain.badge.repository.BadgeRepository;
import com.backend_catcheat.global.s3.S3PresignedUrlService;
import com.backend_catcheat.domain.challenge.dto.PresetBadgeDTO;
import com.backend_catcheat.domain.challenge.dto.RewardBadgeDTO;
import com.backend_catcheat.domain.challenge.dto.RewardBadgeCreateRequestDTO;
import com.backend_catcheat.domain.challenge.dto.RewardBadgeCreateResponseDTO;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 챌린지 보상 뱃지 생성 — 개설 전 단계
 */
@Service
@RequiredArgsConstructor
public class ChallengeRewardBadgeService {
    private final BadgeRepository badgeRepository;
    private final S3PresignedUrlService s3PresignedUrlService;

    /** 개설 화면에 노출할 프리셋 목록 */
    @Transactional(readOnly = true)
    public List<PresetBadgeDTO> getPresets() {
        return badgeRepository.findByConditionType(BadgeConditionType.CHALLENGE_PRESET).stream()
                .map(b -> new PresetBadgeDTO(b.getCode(), b.getName()))
                .toList();
    }

    @Transactional
    public RewardBadgeCreateResponseDTO create(RewardBadgeCreateRequestDTO request) {
        String name = request.name() == null ? null : request.name().trim();
        if (name == null || name.isEmpty()) {
            throw new CustomException(ErrorCode.REWARD_BADGE_NAME_REQUIRED);
        }
        if (name.length() > 100) {   // badge.name VARCHAR(100) — FE 우회 대비 서버 검증
            throw new CustomException(ErrorCode.REWARD_BADGE_NAME_TOO_LONG);
        }

        boolean hasPreset = request.presetCode() != null && !request.presetCode().isBlank();
        boolean hasImage = request.imageKey() != null && !request.imageKey().isBlank();
        //프리셋과 이미지 중 정확히 하나만
        if (hasPreset == hasImage) {
            throw new CustomException(ErrorCode.REWARD_BADGE_SOURCE_REQUIRED);
        }

        Badge badge;
        if (hasPreset) {
            validatePreset(request.presetCode());
            badge = Badge.presetReward(name, request.presetCode());
        } else {
            badge = Badge.drawnReward(name, request.imageKey());
        }
        return new RewardBadgeCreateResponseDTO(badgeRepository.save(badge).getId());
    }

    /** 보상 뱃지 표시 정보 — 상세 미리보기·완료 팝업 */
    @Transactional(readOnly = true)
    public RewardBadgeDTO getRewardBadge(Long badgeId) {
        return badgeRepository.findById(badgeId)
                .map(b -> new RewardBadgeDTO(
                        b.getId(), b.getName(), b.getCode(),
                        s3PresignedUrlService.createDownloadUrl(b.getImageUrl())))
                .orElseThrow(() -> new CustomException(ErrorCode.REWARD_BADGE_NOT_FOUND));
    }

    //임의 code 주입 방지 — 시드된 프리셋 code만 허용
    private void validatePreset(String presetCode) {
        boolean allowed = badgeRepository.findByConditionType(BadgeConditionType.CHALLENGE_PRESET).stream()
                .anyMatch(b -> presetCode.equals(b.getCode()));
        if (!allowed) {
            throw new CustomException(ErrorCode.INVALID_PRESET_CODE);
        }
    }
}
