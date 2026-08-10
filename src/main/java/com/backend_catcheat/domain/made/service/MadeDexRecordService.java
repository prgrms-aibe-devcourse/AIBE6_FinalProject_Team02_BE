package com.backend_catcheat.domain.made.service;

import com.backend_catcheat.domain.auth.entity.User;
import com.backend_catcheat.domain.auth.repository.UserRepository;
import com.backend_catcheat.domain.made.dto.MadeDexRecordCreateRequestDTO;
import com.backend_catcheat.domain.made.dto.MadeDexRecordCreateRequestDTO.PhotoInput;
import com.backend_catcheat.domain.made.dto.MadeDexRecordUpdateRequestDTO.KeptPhoto;
import com.backend_catcheat.domain.made.dto.MadeDexRecordCreateResponseDTO;
import com.backend_catcheat.domain.made.dto.MadeDexRecordDetailDTO;
import com.backend_catcheat.domain.made.dto.MadeDexRecordPhotoDTO;
import com.backend_catcheat.domain.made.dto.MadeDexRecordUpdateRequestDTO;
import com.backend_catcheat.domain.made.entity.MadeDexRecord;
import com.backend_catcheat.domain.made.entity.MadeDexRecordFood;
import com.backend_catcheat.domain.made.entity.MadeDexRecordPhoto;
import com.backend_catcheat.domain.made.entity.MadeDexSlot;
import com.backend_catcheat.domain.made.repository.MadeDexMemberRepository;
import com.backend_catcheat.domain.made.repository.MadeDexRecordFoodRepository;
import com.backend_catcheat.domain.made.repository.MadeDexRecordPhotoRepository;
import com.backend_catcheat.domain.made.repository.MadeDexRecordRepository;
import com.backend_catcheat.domain.made.repository.MadeDexSlotRepository;
import com.backend_catcheat.domain.upload.dto.UploadPurpose;
import com.backend_catcheat.domain.upload.service.UploadObjectService;
import com.backend_catcheat.global.config.TimeConfig;
import com.backend_catcheat.global.event.S3ObjectUnusedEvent;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import com.backend_catcheat.global.s3.S3PresignedUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MadeDexRecordService {

    private final MadeDexRecordRepository madeDexRecordRepository;
    private final MadeDexRecordPhotoRepository madeDexRecordPhotoRepository;
    private final MadeDexRecordFoodRepository madeDexRecordFoodRepository;
    private final MadeDexSlotRepository madeDexSlotRepository;
    private final MadeDexMemberRepository madeDexMemberRepository;
    private final MadeDexFinder madeDexFinder;
    private final UserRepository userRepository;
    private final S3PresignedUrlService s3PresignedUrlService;
    private final UploadObjectService uploadObjectService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public MadeDexRecordCreateResponseDTO create(Long userId, Long madeDexId,
                                                 MadeDexRecordCreateRequestDTO request) {
        requireMember(madeDexId, userId);
        MadeDexSlot slot = writableSlot(madeDexId, request.slotId());
        LocalDate loggedOn = validLoggedOn(request.loggedOn());
        List<PhotoInput> photos = validPhotos(userId, request.photos(), true);
        List<String> foodNames = validFoodNames(request.foodNames());

        MadeDexRecord record = madeDexRecordRepository.save(MadeDexRecord.write(
                madeDexId, slot.getId(), userId, loggedOn, loggedAt(loggedOn, request.loggedTime()),
                validLocationName(request.locationName()), request.lat(), request.lng()));

        savePhotos(record.getId(), photos, 0);
        saveFoods(record.getId(), foodNames);

        return new MadeDexRecordCreateResponseDTO(record.getId());
    }

    @Transactional
    public void update(Long userId, Long madeDexId, Long recordId,
                       MadeDexRecordUpdateRequestDTO request) {
        MadeDexRecord record = authoredRecord(userId, madeDexId, recordId);
        MadeDexSlot slot = writableSlot(madeDexId, request.slotId());
        LocalDate loggedOn = validLoggedOn(request.loggedOn());
        List<String> foodNames = validFoodNames(request.foodNames());

        List<MadeDexRecordPhoto> current =
                madeDexRecordPhotoRepository.findByRecordIdOrderBySortOrderAsc(recordId);
        List<KeptPhoto> keepRequests = request.keepPhotos() == null ? List.of() : request.keepPhotos();
        List<MadeDexRecordPhoto> kept = keptPhotos(current, keepRequests);
        List<PhotoInput> newPhotos = validPhotos(userId, request.newPhotos(), false);
        requirePhotoCount(kept.size() + newPhotos.size());

        record.update(slot.getId(), loggedOn, loggedAt(loggedOn, request.loggedTime()),
                validLocationName(request.locationName()), request.lat(), request.lng());

        Set<Long> keptIds = kept.stream().map(MadeDexRecordPhoto::getId).collect(Collectors.toSet());
        List<MadeDexRecordPhoto> dropped = current.stream()
                .filter(photo -> !keptIds.contains(photo.getId()))
                .toList();
        madeDexRecordPhotoRepository.deleteAll(dropped);

        // 글이 없는 사진이 흔하고 Collectors.toMap은 null 값을 받지 않는다
        Map<Long, String> captionById = new HashMap<>();
        for (KeptPhoto keep : keepRequests) {
            if (keep.photoId() != null) {
                captionById.put(keep.photoId(), validCaption(keep.caption()));
            }
        }

        int order = 0;
        for (MadeDexRecordPhoto photo : kept) {
            photo.moveTo(order++);
            photo.writeCaption(captionById.get(photo.getId()));
        }
        savePhotos(recordId, newPhotos, order);

        madeDexRecordFoodRepository.deleteByRecordId(recordId);
        saveFoods(recordId, foodNames);

        dropped.forEach(photo -> publishIfOrphan(recordId, photo.getImageKey()));
    }

    /**
     * 기록만 소프트 삭제한다. 사진·음식명 행은 남는다.
     * 슬롯 삭제 판정이 지운 기록까지 세고 있어(FK가 살아 있다) 여기서 지우면 그 전제가 깨진다.
     */
    @Transactional
    public void delete(Long userId, Long madeDexId, Long recordId) {
        MadeDexRecord record = authoredRecord(userId, madeDexId, recordId);
        record.delete(LocalDateTime.now(clock));

        madeDexRecordPhotoRepository.findByRecordIdOrderBySortOrderAsc(recordId)
                .forEach(photo -> publishIfOrphan(recordId, photo.getImageKey()));
    }

    /** 같은 사진을 쓰는 살아 있는 기록이 남아 있으면 지우지 않는다 */
    private void publishIfOrphan(Long recordId, String imageKey) {
        if (madeDexRecordPhotoRepository.existsInOtherActiveRecord(imageKey, recordId)) {
            return;
        }
        eventPublisher.publishEvent(new S3ObjectUnusedEvent(imageKey));
    }

    /** 요청한 순서대로 유지할 사진을 고른다. 이 기록의 사진이 아니면 거절한다 */
    private List<MadeDexRecordPhoto> keptPhotos(List<MadeDexRecordPhoto> current, List<KeptPhoto> keepPhotos) {
        if (keepPhotos.isEmpty()) {
            return List.of();
        }
        Map<Long, MadeDexRecordPhoto> byId = current.stream()
                .collect(Collectors.toMap(MadeDexRecordPhoto::getId, Function.identity()));
        return keepPhotos.stream()
                .map(KeptPhoto::photoId)
                .distinct()
                .map(photoId -> {
                    MadeDexRecordPhoto photo = byId.get(photoId);
                    if (photo == null) {
                        throw new CustomException(ErrorCode.MADE_DEX_RECORD_PHOTO_NOT_FOUND);
                    }
                    return photo;
                })
                .toList();
    }

    public MadeDexRecordDetailDTO findDetail(Long userId, Long madeDexId, Long recordId) {
        madeDexFinder.readable(userId, madeDexId);

        MadeDexRecord record = madeDexRecordRepository.findByIdAndDeletedAtIsNull(recordId)
                .orElseThrow(() -> new CustomException(ErrorCode.MADE_DEX_RECORD_NOT_FOUND));
        // 다른 그룹의 기록 id를 넣어도 남의 식탁이 드러나지 않는다
        if (!record.belongsTo(madeDexId)) {
            throw new CustomException(ErrorCode.MADE_DEX_RECORD_NOT_FOUND);
        }

        List<MadeDexRecordPhotoDTO> photos = madeDexRecordPhotoRepository
                .findByRecordIdOrderBySortOrderAsc(recordId).stream()
                .map(photo -> new MadeDexRecordPhotoDTO(
                        photo.getId(),
                        s3PresignedUrlService.createDownloadUrl(photo.getImageKey()),
                        photo.getCaption()))
                .toList();
        List<String> foodNames = madeDexRecordFoodRepository
                .findByRecordIdOrderBySortOrderAsc(recordId).stream()
                .map(MadeDexRecordFood::getFoodName)
                .toList();
        String slotName = madeDexSlotRepository.findById(record.getSlotId())
                .map(MadeDexSlot::getName)
                .orElse(null);
        User author = userRepository.findById(record.getAuthorId()).orElse(null);

        return new MadeDexRecordDetailDTO(
                record.getId(),
                record.getSlotId(),
                slotName,
                record.getLoggedOn(),
                record.getAuthorId(),
                author == null ? null : author.getNickname(),
                record.isAuthor(userId),
                photos,
                foodNames,
                record.getLocationName(),
                record.getLat(),
                record.getLng(),
                record.getLoggedAt());
    }

    private MadeDexRecord authoredRecord(Long userId, Long madeDexId, Long recordId) {
        requireMember(madeDexId, userId);

        MadeDexRecord record = madeDexRecordRepository.findActiveByIdForUpdate(recordId)
                .orElseThrow(() -> new CustomException(ErrorCode.MADE_DEX_RECORD_NOT_FOUND));
        if (!record.belongsTo(madeDexId)) {
            throw new CustomException(ErrorCode.MADE_DEX_RECORD_NOT_FOUND);
        }
        // 그룹장도 남의 기록은 건드리지 못한다
        if (!record.isAuthor(userId)) {
            throw new CustomException(ErrorCode.MADE_DEX_RECORD_NOT_AUTHOR);
        }
        return record;
    }

    /** 숨긴 슬롯에는 새로 쓸 수 없다. 과거 기록 열람은 되지만 하루 화면에서는 빠진 칸이다 */
    private MadeDexSlot writableSlot(Long madeDexId, Long slotId) {
        if (slotId == null) {
            throw new CustomException(ErrorCode.MADE_DEX_SLOT_NOT_FOUND);
        }
        MadeDexSlot slot = madeDexSlotRepository.findById(slotId)
                .orElseThrow(() -> new CustomException(ErrorCode.MADE_DEX_SLOT_NOT_FOUND));
        // 복합 FK가 DB에서도 막지만, 여기서 걸러야 500 대신 제대로 답한다
        if (!slot.belongsTo(madeDexId)) {
            throw new CustomException(ErrorCode.MADE_DEX_SLOT_NOT_FOUND);
        }
        if (slot.isHidden()) {
            throw new CustomException(ErrorCode.MADE_DEX_SLOT_HIDDEN);
        }
        return slot;
    }

    private void requireMember(Long madeDexId, Long userId) {
        madeDexFinder.active(madeDexId);
        if (!madeDexMemberRepository.existsByMadeDexIdAndUserId(madeDexId, userId)) {
            throw new CustomException(ErrorCode.MADE_DEX_NOT_MEMBER);
        }
    }

    private void savePhotos(Long recordId, List<PhotoInput> inputs, int startOrder) {
        List<MadeDexRecordPhoto> photos = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            PhotoInput input = inputs.get(i);
            photos.add(MadeDexRecordPhoto.of(
                    recordId, input.imageKey(), validCaption(input.caption()), startOrder + i));
        }
        madeDexRecordPhotoRepository.saveAll(photos);
    }

    private void saveFoods(Long recordId, List<String> foodNames) {
        List<MadeDexRecordFood> foods = new ArrayList<>(foodNames.size());
        for (int order = 0; order < foodNames.size(); order++) {
            foods.add(MadeDexRecordFood.of(recordId, foodNames.get(order), order));
        }
        madeDexRecordFoodRepository.saveAll(foods);
    }

    private LocalDate validLoggedOn(LocalDate loggedOn) {
        if (loggedOn == null) {
            throw new CustomException(ErrorCode.MADE_DEX_RECORD_DATE_REQUIRED);
        }
        if (loggedOn.isAfter(LocalDate.now(clock.withZone(TimeConfig.SERVICE_ZONE)))) {
            throw new CustomException(ErrorCode.MADE_DEX_RECORD_FUTURE_DATE);
        }
        return loggedOn;
    }

    private List<PhotoInput> validPhotos(Long userId, List<PhotoInput> rawPhotos, boolean required) {
        // 같은 key가 두 번 오면 한 객체를 가리키는 행이 둘 생겨 장수 표시가 부풀고 정리 판정도 어긋난다
        Set<String> seen = new HashSet<>();
        List<PhotoInput> photos = rawPhotos == null ? List.of() : rawPhotos.stream()
                .filter(Objects::nonNull)
                .filter(photo -> blankToNull(photo.imageKey()) != null)
                .filter(photo -> seen.add(photo.imageKey().trim()))
                .map(photo -> new PhotoInput(photo.imageKey().trim(), photo.caption()))
                .toList();
        if (required) {
            requirePhotoCount(photos.size());
        }
        List<String> imageKeys = photos.stream().map(PhotoInput::imageKey).toList();
        if (imageKeys.stream().anyMatch(key -> key.length() > MadeDexRecordPhoto.IMAGE_KEY_MAX)) {
            throw new CustomException(ErrorCode.MADE_DEX_IMAGE_KEY_TOO_LONG);
        }
        photos.forEach(photo -> validCaption(photo.caption()));
        // key만 알면 남의 사진을 자기 기록에 붙일 수 있어, 발급받은 사람이 맞는지 본다
        uploadObjectService.requireUsableBy(userId, imageKeys, UploadPurpose.LOGIT_RECORD);
        return photos;
    }

    /** 적지 않으면 시각을 남기지 않는다. 날짜는 기록한 날을 그대로 쓴다 */
    private LocalDateTime loggedAt(LocalDate loggedOn, LocalTime loggedTime) {
        return loggedTime == null ? null : loggedOn.atTime(loggedTime);
    }

    private void requirePhotoCount(int count) {
        if (count < MadeDexRecord.MIN_PHOTOS) {
            throw new CustomException(ErrorCode.MADE_DEX_RECORD_PHOTO_REQUIRED);
        }
        if (count > MadeDexRecord.MAX_PHOTOS) {
            throw new CustomException(ErrorCode.MADE_DEX_RECORD_PHOTO_TOO_MANY);
        }
    }

    /** 음식명은 더 이상 기록 화면에서 받지 않는다. 예전 기록과 냉장고를 위해 받기만 한다 */
    private List<String> validFoodNames(List<String> rawFoodNames) {
        List<String> foodNames = rawFoodNames == null ? List.of() : rawFoodNames.stream()
                .map(this::blankToNull)
                .filter(Objects::nonNull)
                .toList();
        if (foodNames.stream().anyMatch(name -> name.length() > MadeDexRecord.FOOD_NAME_MAX)) {
            throw new CustomException(ErrorCode.MADE_DEX_RECORD_FOOD_NAME_TOO_LONG);
        }
        return foodNames;
    }

    private String validCaption(String rawCaption) {
        String caption = blankToNull(rawCaption);
        if (caption != null && caption.length() > MadeDexRecordPhoto.CAPTION_MAX) {
            throw new CustomException(ErrorCode.MADE_DEX_RECORD_CAPTION_TOO_LONG);
        }
        return caption;
    }

    private String validLocationName(String rawLocationName) {
        String locationName = blankToNull(rawLocationName);
        if (locationName != null && locationName.length() > MadeDexRecord.LOCATION_NAME_MAX) {
            throw new CustomException(ErrorCode.MADE_DEX_RECORD_LOCATION_TOO_LONG);
        }
        return locationName;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
