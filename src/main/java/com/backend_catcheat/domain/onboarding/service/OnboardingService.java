package com.backend_catcheat.domain.onboarding.service;

import com.backend_catcheat.domain.auth.entity.User;
import com.backend_catcheat.domain.onboarding.dto.OnboardingStatusResponse;
import com.backend_catcheat.domain.auth.repository.UserRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OnboardingService {
    private final UserRepository userRepository;

    public OnboardingStatusResponse getStatus(Long userId) {
        User user = findUser(userId);
        return new OnboardingStatusResponse(user.isOnboardingCompleted());
    }

    // 온보딩 완료 처리
    @Transactional
    public OnboardingStatusResponse completeOnboarding(Long userId) {
        User user = findUser(userId);
        user.completeOnboarding();
        return new OnboardingStatusResponse(user.isOnboardingCompleted());
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
