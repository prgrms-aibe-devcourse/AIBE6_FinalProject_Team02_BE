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
import com.backend_catcheat.domain.made.entity.MadeDexRecordPhoto;
import com.backend_catcheat.domain.made.entity.MadeDexSlot;
import com.backend_catcheat.domain.made.repository.MadeDexMemberRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
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
        LocalDate loggedOn = requireToday(request.loggedOn());
        List<PhotoInput> photos = validPhotos(userId, request.photos(), true);
        requireSlotFree(madeDexId, slot.getId(), userId, loggedOn, null);

        MadeDexRecord record = saveRecord(MadeDexRecord.write(
                madeDexId, slot.getId(), userId, loggedOn, loggedAt(loggedOn, request.loggedTime())));

        savePhotos(record.getId(), photos, 0);

        return new MadeDexRecordCreateResponseDTO(record.getId());
    }

    @Transactional
    public void update(Long userId, Long madeDexId, Long recordId,
                       MadeDexRecordUpdateRequestDTO request) {
        MadeDexRecord record = authoredRecord(userId, madeDexId, recordId);
        MadeDexSlot slot = writableSlot(madeDexId, request.slotId());
        // 날짜는 만든 뒤 바뀌지 않는다. 요청에 담기지 않아 옮길 방법 자체가 없다
        LocalDate loggedOn = record.getLoggedOn();
        boolean past = !loggedOn.equals(today());

        List<MadeDexRecordPhoto> current =
                madeDexRecordPhotoRepository.findByRecordIdOrderBySortOrderAsc(recordId);
        List<KeptPhoto> keepRequests = request.keepPhotos() == null ? List.of() : request.keepPhotos();
        List<MadeDexRecordPhoto> kept = keptPhotos(current, keepRequests);
        List<PhotoInput> newPhotos = validPhotos(userId, request.newPhotos(), false);
        requirePhotoCount(kept.size() + newPhotos.size());

        if (past) {
            requireCaptionOnly(record, slot, current, kept, newPhotos, request.loggedTime());
        } else if (!slot.getId().equals(record.getSlotId())) {
            // 끼니를 옮기면 그쪽이 이미 차 있을 수 있다. 자기 자신은 셈에서 뺀다
            requireSlotFree(madeDexId, slot.getId(), userId, loggedOn, recordId);
        }

        record.update(slot.getId(), loggedOn, loggedAt(loggedOn, request.loggedTime()));

        Set<Long> keptIds = kept.stream().map(MadeDexRecordPhoto::getId).collect(Collectors.toSet());
        List<MadeDexRecordPhoto> dropped = current.stream()
                .filter(photo -> !keptIds.contains(photo.getId()))
                .toList();
        madeDexRecordPhotoRepository.deleteAll(dropped);

        Map<Long, KeptPhoto> keepById = new HashMap<>();
        for (KeptPhoto keep : keepRequests) {
            if (keep.photoId() != null) {
                keepById.put(keep.photoId(), keep);
            }
        }

        int order = 0;
        for (MadeDexRecordPhoto photo : kept) {
            photo.moveTo(order++);
            KeptPhoto keep = keepById.get(photo.getId());
            if (keep != null) {
                photo.writeCaption(validCaption(keep.caption()));
                // 지난 기록은 위치 조정도 잠근다. requireCaptionOnly가 이미 걸러 주지만
                // 값이 흘러들지 않게 여기서도 오늘일 때만 쓴다
                if (!past) {
                    photo.writeCrop(clampCrop(keep.cropX()), clampCrop(keep.cropY()));
                }
            }
        }
        savePhotos(recordId, newPhotos, order);

        dropped.forEach(photo -> publishIfOrphan(recordId, photo.getImageKey()));
    }

    /**
     * 기록만 소프트 삭제한다. 사진 행은 남는다.
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
                        photo.getCaption(),
                        photo.getCropX(),
                        photo.getCropY()))
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
                    recordId, input.imageKey(), validCaption(input.caption()), startOrder + i,
                    clampCrop(input.cropX()), clampCrop(input.cropY())));
        }
        madeDexRecordPhotoRepository.saveAll(photos);
    }

    /**
     * 로그잇은 오늘 먹은 것을 나누는 앱이라 등록은 오늘만 받는다.
     * 요청의 loggedOn은 "언제 걸로 남길지 고르는 값"이 아니라
     * "클라이언트가 지금 며칠이라 믿는지"를 확인하는 값이다.
     * 서버 시각만 쓰면 자정을 넘긴 제출이 조용히 다음 날로 넘어가고,
     * 그 끼니가 선점돼 정작 그날 기록이 막힌다. 그래서 받아서 대조한다.
     */
    private LocalDate requireToday(LocalDate loggedOn) {
        if (loggedOn == null) {
            throw new CustomException(ErrorCode.MADE_DEX_RECORD_DATE_REQUIRED);
        }
        LocalDate today = today();
        if (loggedOn.isAfter(today)) {
            throw new CustomException(ErrorCode.MADE_DEX_RECORD_FUTURE_DATE);
        }
        if (loggedOn.isBefore(today)) {
            throw new CustomException(ErrorCode.MADE_DEX_RECORD_PAST_DATE);
        }
        return loggedOn;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(TimeConfig.SERVICE_ZONE));
    }

    /** 한 사람이 한 끼니에 남기는 건 하루 한 건이다. 지운 기록은 자리를 비켜 준다 */
    private void requireSlotFree(Long madeDexId, Long slotId, Long userId,
                                 LocalDate loggedOn, Long exceptRecordId) {
        boolean taken = exceptRecordId == null
                ? madeDexRecordRepository.existsByMadeDexIdAndSlotIdAndAuthorIdAndLoggedOnAndDeletedAtIsNull(
                        madeDexId, slotId, userId, loggedOn)
                : madeDexRecordRepository.existsByMadeDexIdAndSlotIdAndAuthorIdAndLoggedOnAndDeletedAtIsNullAndIdNot(
                        madeDexId, slotId, userId, loggedOn, exceptRecordId);
        if (taken) {
            throw new CustomException(ErrorCode.MADE_DEX_RECORD_SLOT_TAKEN);
        }
    }

    /**
     * 선검사를 통과한 요청 둘이 동시에 들어오면 유니크 인덱스가 마지막으로 막는다.
     * 그대로 두면 500이 나가므로 선검사와 같은 답으로 바꾼다.
     * saveAndFlush로 즉시 내보내야 여기서 잡힌다 — save만 쓰면 커밋 시점에 터진다.
     */
    private MadeDexRecord saveRecord(MadeDexRecord record) {
        try {
            return madeDexRecordRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException collision) {
            throw new CustomException(ErrorCode.MADE_DEX_RECORD_SLOT_TAKEN);
        }
    }

    /**
     * 지난 기록은 사진에 붙인 글만 고친다.
     * 사진을 더하거나 빼거나 순서를 바꾸는 것, 끼니와 시각을 옮기는 것은 모두 막는다.
     * crop도 사진 쪽으로 묶는다 — 사진의 어느 부분이 보이는지가 바뀌는 값이다.
     */
    private void requireCaptionOnly(MadeDexRecord record, MadeDexSlot slot,
                                    List<MadeDexRecordPhoto> current, List<MadeDexRecordPhoto> kept,
                                    List<PhotoInput> newPhotos, LocalTime loggedTime) {
        if (!newPhotos.isEmpty()) {
            throw new CustomException(ErrorCode.MADE_DEX_RECORD_PAST_LOCKED);
        }
        if (!slot.getId().equals(record.getSlotId())) {
            throw new CustomException(ErrorCode.MADE_DEX_RECORD_PAST_LOCKED);
        }
        if (!Objects.equals(loggedAt(record.getLoggedOn(), loggedTime), record.getLoggedAt())) {
            throw new CustomException(ErrorCode.MADE_DEX_RECORD_PAST_LOCKED);
        }
        // 남길 사진이 원래 집합과 순서까지 같아야 한다
        if (kept.size() != current.size()) {
            throw new CustomException(ErrorCode.MADE_DEX_RECORD_PAST_LOCKED);
        }
        for (int i = 0; i < current.size(); i++) {
            if (!current.get(i).getId().equals(kept.get(i).getId())) {
                throw new CustomException(ErrorCode.MADE_DEX_RECORD_PAST_LOCKED);
            }
        }
    }

    private List<PhotoInput> validPhotos(Long userId, List<PhotoInput> rawPhotos, boolean required) {
        // 같은 key가 두 번 오면 한 객체를 가리키는 행이 둘 생겨 장수 표시가 부풀고 정리 판정도 어긋난다
        Set<String> seen = new HashSet<>();
        List<PhotoInput> photos = rawPhotos == null ? List.of() : rawPhotos.stream()
                .filter(Objects::nonNull)
                .filter(photo -> blankToNull(photo.imageKey()) != null)
                .filter(photo -> seen.add(photo.imageKey().trim()))
                .map(photo -> new PhotoInput(photo.imageKey().trim(), photo.caption(), photo.cropX(), photo.cropY()))
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

    private String validCaption(String rawCaption) {
        String caption = blankToNull(rawCaption);
        if (caption != null && caption.length() > MadeDexRecordPhoto.CAPTION_MAX) {
            throw new CustomException(ErrorCode.MADE_DEX_RECORD_CAPTION_TOO_LONG);
        }
        return caption;
    }

    private double clampCrop(Double value) {
        if (value == null) return 50;
        return Math.max(0, Math.min(100, value));
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
