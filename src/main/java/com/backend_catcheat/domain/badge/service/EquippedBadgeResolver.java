package com.backend_catcheat.domain.badge.service;

import com.backend_catcheat.domain.auth.entity.User;
import com.backend_catcheat.domain.badge.dto.EquippedBadgeViewDTO;
import com.backend_catcheat.domain.badge.repository.BadgeRepository;
import com.backend_catcheat.global.s3.S3PresignedUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EquippedBadgeResolver {
    private final BadgeRepository badgeRepository;
    private final S3PresignedUrlService s3PresignedUrlService;

    /** 유저의 대표 뱃지 표시정보 */
    public EquippedBadgeViewDTO resolve(User user) {
        Long badgeId = user.getEquippedBadgeId();
        if (badgeId == null) {
            return null;
        }
        return badgeRepository.findById(badgeId)
                .map(b -> new EquippedBadgeViewDTO(
                        b.getName(),
                        b.getCode(),
                        // 커스텀 뱃지는 image_url이 S3 key → 프리사인, 프리셋은 null
                        s3PresignedUrlService.createDownloadUrl(b.getImageUrl())))
                .orElse(null);
    }
}
