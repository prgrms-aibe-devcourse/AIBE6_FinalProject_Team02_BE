package com.backend_catcheat.domain.badge.event;

import com.backend_catcheat.domain.badge.service.BadgeGrantService;
import com.backend_catcheat.global.event.SlotsUnlockedEvent;
import com.backend_catcheat.global.event.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 뱃지 자동 지급 트리거
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BadgeGrantEventListener {
    private final BadgeGrantService badgeGrantService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserRegistered(UserRegisteredEvent event) {
        try {
            badgeGrantService.grantSignup(event.userId());
        } catch (Exception e) {
            // 지급 실패가 가입/로그인에 영향 주지 않도록 함
            log.warn("[뱃지] 가입 뱃지 지급 실패 userId={}", event.userId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSlotsUnlocked(SlotsUnlockedEvent event) {
        try {
            badgeGrantService.evaluateCollectionBadges(event.userId());
        } catch (Exception e) {
            log.warn("[뱃지] 수집 뱃지 평가 실패 userId={}", event.userId(), e);
        }
    }
}
