package com.backend_catcheat.domain.registration.service;

import com.backend_catcheat.domain.admin.entity.FoodRegistrationRequest;
import com.backend_catcheat.domain.admin.repository.FoodRegistrationRequestRepository;
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
import com.backend_catcheat.global.event.AdminRegistrationRequestEvent;
import com.backend_catcheat.global.event.SlotsUnlockedEvent;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
    private final FoodRegistrationRequestRepository foodRegistrationRequestRepository;
    private final RegistrationPhotoLoader photoLoader;
    private final VisionProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public RegistrationConfirmResponse confirm(
            Long userId, Long registrationId, RegistrationConfirmRequest request) {

        Registration registration = loadOwnDraft(userId, registrationId);
        List<CardInput> cards = validateCards(request.cards());

        LatestAttempt attempt = latestAttempt(registration.getId());
        Map<Long, BasicDexEntity> slots = loadSlots(cards, attempt, registration);

        // 한 사진을 여러 카드에 붙이는 것이 정상이라 key마다 한 번만 저장한다
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
                // 칸에 붙이지 않는다. 관리자가 수락하는 순간에 붙는다
                saved = cardRepository.save(CollectionCard.awaitingReview(
                        registration.getId(), slot.getId(), thumbnail.getId(),
                        memo, locationName(location), lat(location), lng(location), collectedAt));

                FoodRegistrationRequest savedRequest = foodRegistrationRequestRepository.save(FoodRegistrationRequest.builder()
                        .registrationId(registration.getId())
                        .description(slot.getName())
                        .collectionCardId(saved.getId())
                        // 증빙은 AI가 판정했던 바로 그 사진이어야 한다
                        .evidencePhotoId(evidence != null ? evidence.getId() : thumbnail.getId())
                        .build());

                // 슬롯별로 발행 — 한 건에 미검증 슬롯이 여러 개면 관리자 알림도 그만큼 여러 번 간다(의도된 동작)
                eventPublisher.publishEvent(
                        new AdminRegistrationRequestEvent(userId, slot.getName(), savedRequest.getId()));

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

        // 해금이 있으면 수집 뱃지 평가를 트리거한다 (커밋 후 별도 트랜잭션에서 지급)
        if (!unlocked.isEmpty()) {
            eventPublisher.publishEvent(new SlotsUnlockedEvent(userId));
        }

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

    // 가장 최근 회차만 본다. 이전 회차의 통과 기록을 재활용하지 않는다
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
            // 통과하지 못한 칸은 재시도를 다 쓴 뒤에만 수동 폴백으로 넘어간다
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

    private String resolveThumbnailKey(CardInput card, List<String> photoKeys) {
        if (StringUtils.hasText(card.thumbnailKey())) {
            if (!photoKeys.contains(card.thumbnailKey())) {
                throw new CustomException(ErrorCode.PHOTO_NOT_IN_REGISTRATION);
            }
            return card.thumbnailKey();
        }
        return photoKeys.get(0);
    }

    private Map<String, Photo> persistPhotos(Registration registration, List<CardInput> cards) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(registration.getAnalysisPhotoKey());
        for (CardInput card : cards) {
            keys.addAll(resolveCardPhotoKeys(card, registration));
        }

        Map<String, Photo> saved = new LinkedHashMap<>();
        for (String key : keys) {
            // 해시가 내용 기반이라 메타데이터로는 안 되고 실제 바이트를 내려받아야 한다
            String hash = photoLoader.hash(photoLoader.loadForStorage(key));
            if (photoRepository.existsByHash(hash)) {
                throw new CustomException(ErrorCode.DUPLICATE_PHOTO);
            }
            saved.put(key, photoRepository.save(Photo.of(registration.getId(), key, hash)));
        }
        return saved;
    }

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

    private record LatestAttempt(Set<Long> submittedSlotIds, Set<Long> matchedSlotIds) {
    }

    private record UnlockResult(UserCollection collection, boolean firstUnlock) {
    }
}
