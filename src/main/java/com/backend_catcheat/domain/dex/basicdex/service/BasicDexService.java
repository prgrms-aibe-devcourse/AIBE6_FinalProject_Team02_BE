package com.backend_catcheat.domain.dex.basicdex.service;

import com.backend_catcheat.domain.admin.entity.RegistrationRequestStatus;
import com.backend_catcheat.domain.admin.repository.FoodRegistrationRequestRepository;
import com.backend_catcheat.domain.dex.basicdex.entity.BasicDexEntity;
import com.backend_catcheat.domain.dex.basicdex.repository.BasicDexRepository;
import com.backend_catcheat.domain.dex.collection.dto.CollectionCardResponseDTO;
import com.backend_catcheat.domain.dex.collection.entity.CardPhoto;
import com.backend_catcheat.domain.dex.collection.entity.CollectionCard;
import com.backend_catcheat.domain.dex.collection.entity.UserCollection;
import com.backend_catcheat.domain.dex.collection.repository.CardPhotoRepository;
import com.backend_catcheat.domain.dex.collection.repository.CollectionCardRepository;
import com.backend_catcheat.domain.dex.collection.repository.UserCollectionRepository;
import com.backend_catcheat.domain.my.dto.MyBasicDexDetailResponseDTO;
import com.backend_catcheat.domain.my.dto.MyBasicDexResponseDTO;
import com.backend_catcheat.domain.registration.entity.Photo;
import com.backend_catcheat.domain.registration.repository.PhotoRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import com.backend_catcheat.global.s3.S3PresignedUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BasicDexService {

    // New 스티커가 붙어 있는 기간
    private static final Duration RECENT_UNLOCK_WINDOW = Duration.ofHours(24);

    private final BasicDexRepository basicDexRepository;
    private final UserCollectionRepository userCollectionRepository;
    private final CollectionCardRepository collectionCardRepository;
    private final CardPhotoRepository cardPhotoRepository;
    private final PhotoRepository photoRepository;
    private final FoodRegistrationRequestRepository foodRegistrationRequestRepository;
    private final S3PresignedUrlService s3PresignedUrlService;

    private String resolveIllustrationLocation(BasicDexEntity entity) {
        if (entity.getIllustrationUrl() != null && !entity.getIllustrationUrl().isBlank()) {
            return entity.getIllustrationUrl();
        }
        String key = entity.getCategory().getIllustrationFolderName() + "/" + entity.getName() + ".png";
        return Normalizer.normalize(key, Normalizer.Form.NFD);
    }

    /** 내 도감 -> New·검토대기 스티커에 쓰는 상태가 함께 실림 */
    @Transactional(readOnly = true)
    public List<MyBasicDexResponseDTO> findMyBasicDex(Long userId) {
        // 같은 칸에 요청이 둘 이상 걸릴 수 있어 중복이 섞여 온다
        Set<Long> awaitingReviewSlotIds = new HashSet<>(
                foodRegistrationRequestRepository.findSlotIdsByUserIdAndStatus(
                        userId, RegistrationRequestStatus.PENDING));

        return collectDex(userId, awaitingReviewSlotIds, LocalDateTime.now().minus(RECENT_UNLOCK_WINDOW));
    }

    /**
     * 남의 도감(공개 프로필)
     * 해금 여부와 별 랭크까지만 보여 줌
     */
    @Transactional(readOnly = true)
    public List<MyBasicDexResponseDTO> findPublicBasicDex(Long targetUserId) {
        return collectDex(targetUserId, Set.of(), null);
    }

    private List<MyBasicDexResponseDTO> collectDex(
            Long userId,
            Set<Long> awaitingReviewSlotIds,
            LocalDateTime recentSince
    ) {
        List<BasicDexEntity> slots = basicDexRepository.findAllByOrderByIdAsc();

        Map<Long, UserCollection> collectionBySlotId =  userCollectionRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(UserCollection::getSlotId, Function.identity()));

        return slots.stream()
                .map(slot -> {
                    UserCollection collection = collectionBySlotId.get(slot.getId());
                    boolean unlocked = collection != null;

                    return new MyBasicDexResponseDTO(
                            slot.getId(),
                            slot.getName(),
                            slot.getCategory().getDisplayName(),
                            s3PresignedUrlService.createDownloadUrl(resolveIllustrationLocation(slot)),
                            unlocked,
                            unlocked ? collection.getRank() : 0,
                            unlocked ? collection.getFirstCollectedAt() : null,
                            unlocked ? collectionCardRepository.countByUserCollectionId(collection.getId()) : 0,
                            // 먹은 날이 아니라 칸이 열린 날로 잰다 — 수동 폴백 승인 건이 New를 놓치지 않게
                            // 상세를 열어 확인한 칸은 24시간이 남았어도 뗀다
                            unlocked && recentSince != null
                                    && collection.getCreatedAt().isAfter(recentSince)
                                    && collection.isNewBadgeUnseen(),
                            // "검토 때문에 아직 열리지 않은 칸"이라는 뜻
                            // 이미 열린 칸이면 알릴 것이 없다
                            !unlocked && awaitingReviewSlotIds.contains(slot.getId())
                    );
                })
                .toList();

    }

    @Transactional(readOnly = true)
    public MyBasicDexDetailResponseDTO findMyBasicDexDetail(Long userId, Long slotId) {
        BasicDexEntity slot = basicDexRepository.findById(slotId)
                .orElseThrow(() -> new CustomException(ErrorCode.DEX_SLOT_NOT_FOUND));

        String illustrationUrl = s3PresignedUrlService.createDownloadUrl(resolveIllustrationLocation(slot));

        return userCollectionRepository.findByUserIdAndSlotId(userId, slotId)
                .map(collection -> new MyBasicDexDetailResponseDTO(
                        slot.getId(),
                        slot.getName(),
                        slot.getCategory().getDisplayName(),
                        illustrationUrl,
                        true,
                        collection.getRank(),
                        collection.getFirstCollectedAt(),
                        findCards(collection.getId())
                ))
                .orElseGet(() -> new MyBasicDexDetailResponseDTO(
                        slot.getId(),
                        slot.getName(),
                        slot.getCategory().getDisplayName(),
                        illustrationUrl,
                        false,
                        0,
                        null,
                        List.of()
                ));
    }

    /** New 스티커를 봤다고 표시 */
    @Transactional
    public void markNewBadgeSeen(Long userId, Long slotId) {
        userCollectionRepository.findByUserIdAndSlotId(userId, slotId)
                .ifPresent(collection -> collection.markNewBadgeSeen(LocalDateTime.now()));
    }

    private List<CollectionCardResponseDTO> findCards(Long userCollectionId) {
        return collectionCardRepository.findByUserCollectionIdOrderByCollectedAtDesc(userCollectionId).stream()
                .map(this::toCardResponse)
                .toList();
    }

    private CollectionCardResponseDTO toCardResponse(CollectionCard card) {
        List<CardPhoto> cardPhotos = cardPhotoRepository.findByCollectionCardIdOrderBySortOrderAsc(card.getId());
        List<Long> photoIds = cardPhotos.isEmpty()
                ? List.of(card.getRepresentativePhotoId())
                : cardPhotos.stream().map(CardPhoto::getPhotoId).toList();

        Map<Long, Photo> photoById = photoRepository.findAllById(photoIds).stream()
                .collect(Collectors.toMap(Photo::getId, Function.identity()));

        // 대표 사진(썸네일)이 항상 0번째로 오도록 정렬하고, 나머지는 sortOrder 순 그대로 둔다.
        List<String> photos = photoIds.stream()
                .sorted(Comparator.comparingInt(id -> id.equals(card.getRepresentativePhotoId()) ? 0 : 1))
                .map(id -> {
                    Photo photo = photoById.get(id);
                    if (photo == null) throw new CustomException(ErrorCode.PHOTO_NOT_UPLOADED);
                    return s3PresignedUrlService.createDownloadUrl(photo.getUrl());
                })
                .toList();

        return new CollectionCardResponseDTO(
                card.getId(),
                photos,
                card.getMemo(),
                card.getLocationName(),
                card.getCollectedAt(),
                card.getVerificationStatus().name()
        );
    }
}
