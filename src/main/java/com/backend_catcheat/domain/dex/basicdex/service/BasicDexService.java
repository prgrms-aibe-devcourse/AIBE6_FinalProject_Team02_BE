package com.backend_catcheat.domain.dex.basicdex.service;

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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BasicDexService {
    private final BasicDexRepository basicDexRepository;
    private final UserCollectionRepository userCollectionRepository;
    private final CollectionCardRepository collectionCardRepository;
    private final CardPhotoRepository cardPhotoRepository;
    private final PhotoRepository photoRepository;
    private final S3PresignedUrlService s3PresignedUrlService;

    private String resolveIllustrationLocation(BasicDexEntity entity) {
        if (entity.getIllustrationUrl() != null && !entity.getIllustrationUrl().isBlank()) {
            return entity.getIllustrationUrl();
        }
        String key = entity.getCategory().getIllustrationFolderName() + "/" + entity.getName() + ".png";
        return Normalizer.normalize(key, Normalizer.Form.NFD);
    }

    @Transactional(readOnly = true)
    public List<MyBasicDexResponseDTO> findMyBasicDex(Long userId) {
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
                            unlocked ? collectionCardRepository.countByUserCollectionId(collection.getId()) : 0
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
