package com.backend_catcheat.domain.registration.service;

import com.backend_catcheat.domain.admin.entity.ReviewQueueItem;
import com.backend_catcheat.domain.admin.repository.ReviewQueueItemRepository;
import com.backend_catcheat.domain.dex.basicdex.entity.BasicDexEntity;
import com.backend_catcheat.domain.dex.basicdex.repository.BasicDexRepository;
import com.backend_catcheat.domain.dex.collection.entity.CardPhoto;
import com.backend_catcheat.domain.dex.collection.entity.CollectionCard;
import com.backend_catcheat.domain.dex.collection.entity.UserCollection;
import com.backend_catcheat.domain.dex.collection.repository.CardPhotoRepository;
import com.backend_catcheat.domain.dex.collection.repository.CollectionCardRepository;
import com.backend_catcheat.domain.dex.collection.repository.UserCollectionRepository;
import com.backend_catcheat.domain.registration.config.VisionProperties;
import com.backend_catcheat.domain.registration.dto.RegistrationConfirmRequest;
import com.backend_catcheat.domain.registration.dto.RegistrationConfirmRequest.CardInput;
import com.backend_catcheat.domain.registration.dto.RegistrationConfirmRequest.LocationInput;
import com.backend_catcheat.domain.registration.dto.RegistrationConfirmResponse;
import com.backend_catcheat.domain.registration.dto.RegistrationConfirmResponse.PendingSlot;
import com.backend_catcheat.domain.registration.dto.RegistrationConfirmResponse.UnlockedSlot;
import com.backend_catcheat.domain.registration.entity.Photo;
import com.backend_catcheat.domain.registration.entity.Registration;
import com.backend_catcheat.domain.registration.entity.VerificationAttempt;
import com.backend_catcheat.domain.registration.repository.PhotoRepository;
import com.backend_catcheat.domain.registration.repository.RegistrationRepository;
import com.backend_catcheat.domain.registration.repository.VerificationAttemptRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 음식별 기록을 마친 등록 건을 확정한다.
 *
 * 칸마다 갈림길이 있다:
 *  - AI 검증 통과      → 바로 해금 (verification_status = PHOTO_VERIFIED)
 *  - 불통과 + 재시도 남음 → 거부. 먼저 다시 확인해야 한다
 *  - 불통과 + 상한 소진  → 카드만 만들고 검토 큐로 (MANUAL_PENDING, 칸은 안 열림)
 *
 * 수동 폴백 건은 관리자가 수락해야 칸이 열린다 — 그 전까지 수집률에 잡히지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationConfirmService {

    private static final int MEMO_MAX_LENGTH = 100;

    private final RegistrationRepository registrationRepository;
    private final VerificationAttemptRepository attemptRepository;
    private final PhotoRepository photoRepository;
    private final BasicDexRepository slotRepository;
    private final UserCollectionRepository userCollectionRepository;
    private final CollectionCardRepository cardRepository;
    private final CardPhotoRepository cardPhotoRepository;
    private final ReviewQueueItemRepository reviewQueueRepository;
    private final RegistrationPhotoLoader photoLoader;
    private final VisionProperties properties;

    @Transactional
    public RegistrationConfirmResponse confirm(
            Long userId, Long registrationId, RegistrationConfirmRequest request) {

        Registration registration = loadOwnDraft(userId, registrationId);
        List<CardInput> cards = validateCards(request.cards());

        LatestAttempt attempt = latestAttempt(registration.getId());
        Map<Long, BasicDexEntity> slots = loadSlots(cards, attempt, registration);

        // 사진은 카드 간 중복 첨부가 정상이므로(한 상 사진 1장 → 여러 칸) key마다 한 번만 저장한다
        Map<String, Photo> photosByKey = persistPhotos(registration, cards);
        Photo evidence = photosByKey.get(registration.getAnalysisPhotoKey());

        LocalDateTime collectedAt = LocalDateTime.now();
        LocationInput location = request.location();
        List<UnlockedSlot> unlocked = new ArrayList<>();
        List<PendingSlot> pending = new ArrayList<>();

        for (CardInput card : cards) {
            List<String> photoKeys = resolveCardPhotoKeys(card, registration);
            Photo thumbnail = photosByKey.get(resolveThumbnailKey(card, photoKeys));
            BasicDexEntity slot = slots.get(card.slotId());
            String memo = validateMemo(card.memo());

            CollectionCard saved;
            if (attempt.matchedSlotIds().contains(card.slotId())) {
                UnlockResult unlock = unlockSlot(userId, card.slotId(), collectedAt);
                saved = cardRepository.save(CollectionCard.verified(
                        registration.getId(), unlock.collection().getId(), slot.getId(), thumbnail.getId(),
                        memo, locationName(location), lat(location), lng(location), collectedAt));

                unlocked.add(new UnlockedSlot(
                        slot.getId(), slot.getName(), slot.getCategory().getDisplayName(),
                        saved.getId(), unlock.collection().getRank(), unlock.firstUnlock()));
            } else {
                // 칸에 붙이지 않는다 — 관리자가 수락하는 순간에 붙는다
                saved = cardRepository.save(CollectionCard.awaitingReview(
                        registration.getId(), slot.getId(), thumbnail.getId(),
                        memo, locationName(location), lat(location), lng(location), collectedAt));

                reviewQueueRepository.save(ReviewQueueItem.pending(
                        registration.getId(), saved.getId(),
                        // 증빙은 AI가 판정했던 바로 그 사진이다
                        evidence != null ? evidence.getId() : thumbnail.getId()));

                pending.add(new PendingSlot(
                        slot.getId(), slot.getName(), slot.getCategory().getDisplayName(), saved.getId()));
            }

            List<CardPhoto> links = new ArrayList<>();
            for (int order = 0; order < photoKeys.size(); order++) {
                links.add(CardPhoto.of(saved.getId(), photosByKey.get(photoKeys.get(order)).getId(), order));
            }
            cardPhotoRepository.saveAll(links);
        }

        registration.complete();

        long collectedCount = userCollectionRepository.countByUserId(userId);
        long totalSlots = slotRepository.count();

        log.info("[등록] 확정 registrationId={} 해금{}칸 검토대기{}칸 수집률 {}/{}",
                registration.getId(), unlocked.size(), pending.size(), collectedCount, totalSlots);

        return new RegistrationConfirmResponse(
                registration.getId(), unlocked, pending, collectedCount, totalSlots);
    }

    private Registration loadOwnDraft(Long userId, Long registrationId) {
        Registration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new CustomException(ErrorCode.REGISTRATION_NOT_FOUND));

        // 요청 경로의 id를 신뢰하지 않는다 (§7)
        if (!registration.isOwnedBy(userId)) {
            throw new CustomException(ErrorCode.REGISTRATION_FORBIDDEN);
        }
        // 같은 건을 두 번 확정하면 카드가 중복 생성되고 별 랭크가 부당하게 오른다
        if (registration.isCompleted()) {
            throw new CustomException(ErrorCode.REGISTRATION_ALREADY_COMPLETED);
        }
        return registration;
    }

    private List<CardInput> validateCards(List<CardInput> cards) {
        if (cards == null || cards.isEmpty()) {
            throw new CustomException(ErrorCode.CARD_REQUIRED);
        }
        if (cards.size() > properties.maxFoodNames()) {
            throw new CustomException(ErrorCode.FOOD_NAME_COUNT_EXCEEDED);
        }
        if (cards.stream().map(CardInput::slotId).anyMatch(Objects::isNull)) {
            throw new CustomException(ErrorCode.CARD_REQUIRED);
        }
        if (cards.stream().map(CardInput::slotId).distinct().count() != cards.size()) {
            // 같은 칸을 한 등록 건에서 두 번 열면 별 랭크가 한 끼에 2칸 오른다
            throw new CustomException(ErrorCode.CARD_REQUIRED);
        }
        return cards;
    }

    /** 가장 최근 회차만 본다 — 이전 회차의 통과 기록을 재활용하지 않는다. */
    private LatestAttempt latestAttempt(Long registrationId) {
        List<VerificationAttempt> attempts = attemptRepository.findByRegistrationIdOrderByIdAsc(registrationId);
        int latest = attempts.stream().mapToInt(VerificationAttempt::getAttemptNo).max().orElse(0);
        List<VerificationAttempt> current = attempts.stream()
                .filter(attempt -> attempt.getAttemptNo() == latest)
                .toList();

        return new LatestAttempt(
                current.stream().map(VerificationAttempt::getSlotId).collect(Collectors.toSet()),
                current.stream().filter(VerificationAttempt::isMatched)
                        .map(VerificationAttempt::getSlotId).collect(Collectors.toSet()));
    }

    private Map<Long, BasicDexEntity> loadSlots(
            List<CardInput> cards, LatestAttempt attempt, Registration registration) {

        for (CardInput card : cards) {
            // 검증에 올리지도 않은 칸을 끼워 넣을 수 없어야 한다
            if (!attempt.submittedSlotIds().contains(card.slotId())) {
                throw new CustomException(ErrorCode.SLOT_NOT_VERIFIED);
            }
            // 통과하지 못한 칸은 재시도를 다 쓴 뒤에만 수동 폴백으로 넘어간다 (§5.2)
            if (!attempt.matchedSlotIds().contains(card.slotId()) && registration.canRetry()) {
                throw new CustomException(ErrorCode.SLOT_NOT_VERIFIED);
            }
        }

        List<Long> slotIds = cards.stream().map(CardInput::slotId).toList();
        Map<Long, BasicDexEntity> found = slotRepository.findAllById(slotIds).stream()
                .collect(Collectors.toMap(BasicDexEntity::getId, Function.identity()));

        if (found.size() != slotIds.size()) {
            throw new CustomException(ErrorCode.DEX_SLOT_NOT_FOUND);
        }
        return found;
    }

    /**
     * 카드 사진 미지정 시 분석 사진을 자동 첨부한다 —
     * 아무것도 고르지 않아도 등록이 완료돼야 한다 (§5.2 등록 이탈 방지).
     */
    private List<String> resolveCardPhotoKeys(CardInput card, Registration registration) {
        List<String> keys = card.cardPhotoKeys() == null ? List.of()
                : card.cardPhotoKeys().stream().filter(StringUtils::hasText).distinct().toList();

        if (keys.isEmpty()) {
            return List.of(registration.getAnalysisPhotoKey());
        }
        if (keys.size() > properties.maxImages()) {
            throw new CustomException(ErrorCode.PHOTO_COUNT_EXCEEDED);
        }
        return keys;
    }

    /** 썸네일 미지정 시 첫 번째 카드 사진 (§5.2) */
    private String resolveThumbnailKey(CardInput card, List<String> photoKeys) {
        if (StringUtils.hasText(card.thumbnailKey())) {
            if (!photoKeys.contains(card.thumbnailKey())) {
                throw new CustomException(ErrorCode.PHOTO_NOT_IN_REGISTRATION);
            }
            return card.thumbnailKey();
        }
        return photoKeys.get(0);
    }

    /**
     * 등록 건에서 쓰이는 모든 사진을 저장한다. key당 photo 행 하나 —
     * 같은 사진을 여러 카드에 붙이는 것은 정상이므로 카드 수만큼 복제하지 않는다.
     * 검토 증빙으로 쓸 분석 사진도 반드시 포함시킨다.
     */
    private Map<String, Photo> persistPhotos(Registration registration, List<CardInput> cards) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(registration.getAnalysisPhotoKey());
        for (CardInput card : cards) {
            keys.addAll(resolveCardPhotoKeys(card, registration));
        }

        Map<String, Photo> saved = new LinkedHashMap<>();
        for (String key : keys) {
            // 해시는 내용 기반이라 실제 바이트가 필요하다. 같은 사진 재사용 차단의 기준값 (§5.2)
            String hash = photoLoader.hash(photoLoader.loadForStorage(key));
            if (photoRepository.existsByHash(hash)) {
                throw new CustomException(ErrorCode.DUPLICATE_PHOTO);
            }
            saved.put(key, photoRepository.save(Photo.of(registration.getId(), key, hash)));
        }
        return saved;
    }

    /** 최초면 별 1개로 해금, 이미 열린 칸이면 별을 하나 올린다(최대 3) (§5.1). */
    private UnlockResult unlockSlot(Long userId, Long slotId, LocalDateTime collectedAt) {
        return userCollectionRepository.findByUserIdAndSlotId(userId, slotId)
                .map(existing -> {
                    existing.collectAgain();
                    return new UnlockResult(existing, false);
                })
                .orElseGet(() -> new UnlockResult(
                        userCollectionRepository.save(UserCollection.unlock(userId, slotId, collectedAt)), true));
    }

    private String validateMemo(String memo) {
        if (!StringUtils.hasText(memo)) {
            return null;
        }
        if (memo.length() > MEMO_MAX_LENGTH) {
            throw new CustomException(ErrorCode.MEMO_TOO_LONG);
        }
        return memo;
    }

    private static String locationName(LocationInput location) {
        return location != null ? location.name() : null;
    }

    private static Double lat(LocationInput location) {
        return location != null ? location.lat() : null;
    }

    private static Double lng(LocationInput location) {
        return location != null ? location.lng() : null;
    }

    /**
     * @param submittedSlotIds 이번 회차에 검증을 올린 칸 (통과 여부 무관)
     * @param matchedSlotIds   그중 통과한 칸
     */
    private record LatestAttempt(Set<Long> submittedSlotIds, Set<Long> matchedSlotIds) {
    }

    private record UnlockResult(UserCollection collection, boolean firstUnlock) {
    }
}
