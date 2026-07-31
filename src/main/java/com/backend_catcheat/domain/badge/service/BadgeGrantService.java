package com.backend_catcheat.domain.badge.service;

import com.backend_catcheat.domain.badge.entity.Badge;
import com.backend_catcheat.domain.badge.entity.UserBadge;
import com.backend_catcheat.domain.badge.entity.type.BadgeConditionType;
import com.backend_catcheat.domain.badge.repository.BadgeRepository;
import com.backend_catcheat.domain.badge.repository.UserBadgeRepository;
import com.backend_catcheat.domain.dex.basicdex.repository.BasicDexRepository;
import com.backend_catcheat.domain.dex.basicdex.type.Category;
import com.backend_catcheat.domain.dex.collection.repository.UserCollectionRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 뱃지 자동 지급
 * 지급조건을 하드코딩하지 않고 badge 마스터(condition_type/value)를 읽어 판단
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BadgeGrantService {

    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final UserCollectionRepository userCollectionRepository;
    private final BasicDexRepository basicDexRepository;

    /** 가입 시 기본 뱃지 지급 */
    @Transactional
    public void grantSignup(Long userId) {
        badgeRepository.findByConditionType(BadgeConditionType.SIGNUP)
                .forEach(badge -> grantIfAbsent(userId, badge));
    }

    /**
     * 기본 도감 수집이 늘어난 뒤 호출 — 수집률/카테고리 완성 뱃지를 평가해 미보유분만 지급
     */
    @Transactional
    public void evaluateCollectionBadges(Long userId) {
        long total = basicDexRepository.count();
        long collected = userCollectionRepository.countByUserId(userId);

        // 수집률 임계
        if (total > 0) {
            for (Badge badge : badgeRepository.findByConditionType(BadgeConditionType.COLLECTION_RATE)) {
                int threshold = Integer.parseInt(badge.getConditionValue());
                if (collected * 100 >= (long) threshold * total) {
                    grantIfAbsent(userId, badge);
                }
            }
        }

        // 카테고리 완전수집 (분자 == 분모)
        for (Badge badge : badgeRepository.findByConditionType(BadgeConditionType.CATEGORY_COMPLETE)) {
            Category category = Category.valueOf(badge.getConditionValue());
            long categoryTotal = basicDexRepository.countByCategory(category);
            if (categoryTotal > 0
                    && userCollectionRepository.countByUserIdAndCategory(userId, category) == categoryTotal) {
                grantIfAbsent(userId, badge);
            }
        }
    }

    /** 미보유 시에만 지급 */
    private void grantIfAbsent(Long userId, Badge badge) {
        if (userBadgeRepository.existsByUserIdAndBadge_Id(userId, badge.getId())) {
            return;
        }
        userBadgeRepository.save(UserBadge.grant(userId, badge, LocalDateTime.now()));
        log.info("[뱃지] 지급 userId={} badge={}({})", userId, badge.getCode(), badge.getName());
    }
}
