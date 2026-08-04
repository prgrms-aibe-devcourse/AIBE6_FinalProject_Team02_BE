package com.backend_catcheat.domain.my.service;

import com.backend_catcheat.domain.auth.entity.User;
import com.backend_catcheat.domain.auth.repository.UserRepository;
import com.backend_catcheat.domain.badge.repository.UserBadgeRepository;
import com.backend_catcheat.domain.my.dto.MyBadgeResponse;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import com.backend_catcheat.global.s3.S3PresignedUrlService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마이페이지 뱃지 비즈니스 로직
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyBadgeService {
    private final UserRepository userRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final S3PresignedUrlService s3PresignedUrlService;

    /** 획득 뱃지 목록 (장착 여부 포함, 최신순) */
    public List<MyBadgeResponse> getMyBadges(Long userId) {
        User user = findUser(userId);
        Long equippedId = user.getEquippedBadgeId();

        return userBadgeRepository.findWithBadgeByUserId(userId).stream()
                .map(ub -> new MyBadgeResponse(
                        ub.getBadge().getId(),
                        ub.getBadge().getName(),
                        ub.getBadge().getCode(),
                        // 제작 뱃지는 image_url이 S3 key -> 조회용 프리사인 URL로 변환(프리셋은 null -> code로 렌더)
                        s3PresignedUrlService.createDownloadUrl(ub.getBadge().getImageUrl()),
                        ub.getBadge().getDescription(),
                        ub.getAcquiredAt(),
                        ub.getBadge().getId().equals(equippedId)))
                .toList();
    }

    /** 대표 뱃지 장착/해제 메서드. badgeId가 null이면 해제, 아니면 보유한 뱃지여야 함 */
    @Transactional
    public void equip(Long userId, Long badgeId) {
        User user = findUser(userId);
        if (badgeId != null
                && !userBadgeRepository.existsByUserIdAndBadge_Id(userId, badgeId)) {
            throw new CustomException(ErrorCode.BADGE_NOT_OWNED);
        }
        user.equipBadge(badgeId);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
