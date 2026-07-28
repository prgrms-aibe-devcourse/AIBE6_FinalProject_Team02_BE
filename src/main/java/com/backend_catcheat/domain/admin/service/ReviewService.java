package com.backend_catcheat.domain.admin.service;

import com.backend_catcheat.domain.admin.dto.ReviewItemResponse;
import com.backend_catcheat.domain.admin.entity.ReviewQueueItem;
import com.backend_catcheat.domain.admin.entity.ReviewStatus;
import com.backend_catcheat.domain.admin.repository.ReviewQueueItemRepository;
import com.backend_catcheat.domain.dex.basicdex.repository.BasicDexRepository;
import com.backend_catcheat.domain.dex.collection.entity.CollectionCard;
import com.backend_catcheat.domain.dex.collection.entity.UserCollection;
import com.backend_catcheat.domain.dex.collection.repository.CollectionCardRepository;
import com.backend_catcheat.domain.dex.collection.repository.UserCollectionRepository;
import com.backend_catcheat.domain.registration.repository.PhotoRepository;
import com.backend_catcheat.domain.registration.repository.RegistrationRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import com.backend_catcheat.global.s3.S3PresignedUrlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 수동 등록 건의 관리자 검토.
 *
 * **수락하는 순간이 해금 시점이다** — 그전까지 카드는 칸에 붙지 않고 수집률에도 잡히지 않는다.
 * 검토자가 없으면 기능이 죽으므로 이 API가 곧 수동 폴백의 존재 이유다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewQueueItemRepository reviewQueueRepository;
    private final CollectionCardRepository cardRepository;
    private final UserCollectionRepository userCollectionRepository;
    private final RegistrationRepository registrationRepository;
    private final BasicDexRepository slotRepository;
    private final PhotoRepository photoRepository;
    private final S3PresignedUrlService presignedUrlService;

    @Transactional(readOnly = true)
    public List<ReviewItemResponse> pendingItems() {
        return reviewQueueRepository.findByStatusOrderByIdAsc(ReviewStatus.PENDING).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 수락 — 카드를 칸에 붙인다. 이 시점에 별 랭크가 정해지고 수집률이 오른다.
     * 랭크를 등록 시점이 아니라 여기서 매기는 이유: 대기 중 다른 등록으로 같은 칸이 먼저 열렸을 수 있다.
     */
    @Transactional
    public void approve(Long reviewItemId) {
        ReviewQueueItem item = loadPending(reviewItemId);
        CollectionCard card = cardRepository.findById(item.getCollectionCardId())
                .orElseThrow(() -> new CustomException(ErrorCode.REGISTRATION_NOT_FOUND));

        Long userId = registrationRepository.findById(card.getRegistrationId())
                .orElseThrow(() -> new CustomException(ErrorCode.REGISTRATION_NOT_FOUND))
                .getUserId();

        UserCollection collection = userCollectionRepository
                .findByUserIdAndSlotId(userId, card.getSlotId())
                .map(existing -> {
                    existing.collectAgain();
                    return existing;
                })
                .orElseGet(() -> userCollectionRepository.save(
                        UserCollection.unlock(userId, card.getSlotId(), card.getCollectedAt())));

        card.attachTo(collection.getId());
        item.approve(LocalDateTime.now());

        log.info("[검토] 수락 reviewItemId={} cardId={} userId={} slotId={} rank={}",
                reviewItemId, card.getId(), userId, card.getSlotId(), collection.getRank());
    }

    /**
     * 반려 — 카드는 칸에 붙지 않은 채로 남는다.
     * 카드를 지우지 않는 이유: 유저가 왜 반려됐는지 확인할 수 있어야 하고, 사진도 증빙으로 남아야 한다.
     */
    @Transactional
    public void reject(Long reviewItemId) {
        ReviewQueueItem item = loadPending(reviewItemId);
        item.reject(LocalDateTime.now());

        log.info("[검토] 반려 reviewItemId={} cardId={}", reviewItemId, item.getCollectionCardId());
    }

    private ReviewQueueItem loadPending(Long reviewItemId) {
        ReviewQueueItem item = reviewQueueRepository.findById(reviewItemId)
                .orElseThrow(() -> new CustomException(ErrorCode.REVIEW_ITEM_NOT_FOUND));
        // 이미 처리한 건을 다시 수락하면 별 랭크가 한 번 더 오른다
        if (!item.isPending()) {
            throw new CustomException(ErrorCode.REVIEW_ALREADY_HANDLED);
        }
        return item;
    }

    private ReviewItemResponse toResponse(ReviewQueueItem item) {
        CollectionCard card = cardRepository.findById(item.getCollectionCardId()).orElse(null);
        String slotName = card == null ? null
                : slotRepository.findById(card.getSlotId()).map(slot -> slot.getName()).orElse(null);
        String evidenceUrl = photoRepository.findById(item.getEvidencePhotoId())
                .map(photo -> presignedUrlService.createDownloadUrl(photo.getUrl()))
                .orElse(null);

        return new ReviewItemResponse(
                item.getId(),
                item.getCollectionCardId(),
                card != null ? card.getSlotId() : null,
                slotName,
                card != null ? card.getMemo() : null,
                card != null ? card.getLocationName() : null,
                card != null ? card.getCollectedAt() : null,
                evidenceUrl);
    }
}
