package com.backend_catcheat.domain.onboarding.service;

import com.backend_catcheat.domain.onboarding.dto.GuideSeenResponseDTO;
import com.backend_catcheat.domain.onboarding.repository.UserGuideSeenRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/** 도메인별 코치마크 온보딩을 봤는지 관리 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OnboardingService {
    /** 가이드 키 목록 */
    private static final Set<String> GUIDE_KEYS = Set.of(
            "logit-list",         // 로그잇 목록 /made
            "logit-home",         // 오늘의 식탁 /made/{dexId}
            "basit-category",     // 베이짓 카테고리 목록 /basicDex
            "basit-grid",         // 베이짓 음식 그리드 /basicDex?category=…
            "challengit",         // 챌린짓 홈 /challenge
            "challengit-detail"   // 챌린짓 상세 /challenge/{id}
    );

    private final UserGuideSeenRepository guideSeenRepository;

    /** 본 가이드 키 목록 */
    public GuideSeenResponseDTO getSeenGuides(Long userId) {
        return new GuideSeenResponseDTO(guideSeenRepository.findKeysByUserId(userId));
    }

    /**
     * 가이드를 본 것으로 기록
     */
    @Transactional
    public GuideSeenResponseDTO markGuideSeen(Long userId, String guideKey) {
        if (!GUIDE_KEYS.contains(guideKey)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        guideSeenRepository.insertIfAbsent(userId, guideKey);
        return getSeenGuides(userId);
    }

    /** me 응답에 실어 보낼 용도 */
    public List<String> seenGuideKeys(Long userId) {
        return guideSeenRepository.findKeysByUserId(userId);
    }
}
