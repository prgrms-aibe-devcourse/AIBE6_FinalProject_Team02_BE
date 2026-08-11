package com.backend_catcheat.domain.made.service;

import com.backend_catcheat.domain.auth.entity.User;
import com.backend_catcheat.domain.auth.repository.UserRepository;
import com.backend_catcheat.domain.made.dto.MadeDexFeedCardDTO;
import com.backend_catcheat.domain.made.dto.MadeDexFeedDTO;
import com.backend_catcheat.domain.made.dto.MadeDexFeedSlotDTO;
import com.backend_catcheat.domain.made.entity.MadeDexMember;
import com.backend_catcheat.domain.made.entity.MadeDexRecord;
import com.backend_catcheat.domain.made.entity.MadeDexRecordPhoto;
import com.backend_catcheat.domain.made.entity.MadeDexSlot;
import com.backend_catcheat.domain.made.repository.MadeDexMemberRepository;
import com.backend_catcheat.domain.made.repository.MadeDexRecordPhotoRepository;
import com.backend_catcheat.domain.made.repository.MadeDexRecordRepository;
import com.backend_catcheat.domain.made.repository.MadeDexSlotRepository;
import com.backend_catcheat.global.config.TimeConfig;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import com.backend_catcheat.global.s3.S3PresignedUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MadeDexFeedService {

    private final MadeDexSlotRepository madeDexSlotRepository;
    private final MadeDexMemberRepository madeDexMemberRepository;
    private final MadeDexRecordRepository madeDexRecordRepository;
    private final MadeDexRecordPhotoRepository madeDexRecordPhotoRepository;
    private final MadeDexFinder madeDexFinder;
    private final UserRepository userRepository;
    private final S3PresignedUrlService s3PresignedUrlService;
    private final Clock clock;

    /**
     * 하루치 식탁. 슬롯마다 멤버 카드가 놓이고, 기록이 없는 멤버도 빈 카드로 자리를 남긴다.
     * 쿼리는 슬롯·멤버·기록·사진·음식명·유저 여섯 번으로 고정한다.
     */
    public MadeDexFeedDTO findFeed(Long userId, Long madeDexId, LocalDate date) {
        madeDexFinder.readable(userId, madeDexId);

        LocalDate today = LocalDate.now(clock.withZone(TimeConfig.SERVICE_ZONE));
        LocalDate loggedOn = date == null ? today : date;
        if (loggedOn.isAfter(today)) {
            throw new CustomException(ErrorCode.MADE_DEX_RECORD_FUTURE_DATE);
        }

        List<MadeDexRecord> records = madeDexRecordRepository
                .findByMadeDexIdAndLoggedOnAndDeletedAtIsNullOrderByCreatedAtAsc(madeDexId, loggedOn);

        List<MadeDexFeedCardDTO> emptyCards = emptyCards(madeDexId, userId);
        Map<Long, Map<Long, List<MadeDexRecord>>> bySlotAndAuthor = groupBySlotAndAuthor(records);
        Photos photos = loadPhotos(records);

        return new MadeDexFeedDTO(loggedOn, today, visibleSlots(madeDexId, records).stream()
                .map(slot -> new MadeDexFeedSlotDTO(
                        slot.getId(),
                        slot.getName(),
                        slot.isHidden(),
                        cardsOf(emptyCards, bySlotAndAuthor.getOrDefault(slot.getId(), Map.of()), photos)))
                .toList());
    }

    /**
     * 숨긴 슬롯은 하루 화면에서 빠지지만, 그날 기록이 남아 있으면 보여 준다.
     * 숨겼다고 과거의 기록이 사라지면 안 된다.
     */
    private List<MadeDexSlot> visibleSlots(Long madeDexId, List<MadeDexRecord> records) {
        Set<Long> usedSlotIds = records.stream()
                .map(MadeDexRecord::getSlotId)
                .collect(Collectors.toSet());
        return madeDexSlotRepository.findByMadeDexIdOrderBySortOrderAscIdAsc(madeDexId).stream()
                .filter(slot -> !slot.isHidden() || usedSlotIds.contains(slot.getId()))
                .toList();
    }

    /** 멤버 카드 뼈대. 내가 항상 첫 장이고 나머지는 가입 순서다 */
    private List<MadeDexFeedCardDTO> emptyCards(Long madeDexId, Long userId) {
        List<MadeDexMember> members =
                madeDexMemberRepository.findByMadeDexIdOrderByJoinedAtAscIdAsc(madeDexId);
        Map<Long, User> userById = userRepository
                .findAllById(members.stream().map(MadeDexMember::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return members.stream()
                .sorted(Comparator.comparing((MadeDexMember member) ->
                        member.getUserId().equals(userId)).reversed())
                .map(member -> {
                    User user = userById.get(member.getUserId());
                    return new MadeDexFeedCardDTO(
                            member.getUserId(),
                            user == null ? null : user.getNickname(),
                            user == null ? null : s3PresignedUrlService.createDownloadUrl(user.getProfileImageKey()),
                            member.getUserId().equals(userId),
                            0, null, 50, 50, List.of(), null);
                })
                .toList();
    }

    private List<MadeDexFeedCardDTO> cardsOf(List<MadeDexFeedCardDTO> emptyCards,
                                             Map<Long, List<MadeDexRecord>> byAuthor,
                                             Photos photos) {
        return emptyCards.stream()
                .map(card -> {
                    List<MadeDexRecord> mine = byAuthor.get(card.userId());
                    return mine == null ? card : toCard(card, mine, photos);
                })
                .toList();
    }

    /**
     * 한 사람이 한 슬롯에 남긴 것을 카드 한 장으로 접는다.
     * 대표 사진은 가장 먼저 남긴 기록의 첫 장이다.
     */
    private MadeDexFeedCardDTO toCard(MadeDexFeedCardDTO card, List<MadeDexRecord> records, Photos photos) {
        List<Long> recordIds = records.stream().map(MadeDexRecord::getId).toList();
        MadeDexRecord cover = records.stream()
                .filter(record -> photos.firstKeyOf(record.getId()) != null)
                .findFirst()
                .orElse(records.getFirst());
        MadeDexRecordPhoto coverPhoto = photos.firstOf(cover.getId());
        String thumbnailKey = coverPhoto == null ? null : coverPhoto.getImageKey();
        double cropX = coverPhoto == null ? 50 : coverPhoto.getCropX();
        double cropY = coverPhoto == null ? 50 : coverPhoto.getCropY();

        return new MadeDexFeedCardDTO(
                card.userId(),
                card.nickname(),
                card.profileImageUrl(),
                card.me(),
                records.size(),
                s3PresignedUrlService.createDownloadUrl(thumbnailKey),
                cropX,
                cropY,
                recordIds,
                cover.getLoggedAt());
    }

    private Map<Long, Map<Long, List<MadeDexRecord>>> groupBySlotAndAuthor(List<MadeDexRecord> records) {
        return records.stream().collect(Collectors.groupingBy(
                MadeDexRecord::getSlotId,
                Collectors.groupingBy(MadeDexRecord::getAuthorId)));
    }

    private Photos loadPhotos(List<MadeDexRecord> records) {
        if (records.isEmpty()) {
            return new Photos(Map.of());
        }
        List<Long> recordIds = records.stream().map(MadeDexRecord::getId).toList();
        return new Photos(
                madeDexRecordPhotoRepository.findByRecordIdInOrderBySortOrderAsc(recordIds).stream()
                        .collect(Collectors.groupingBy(MadeDexRecordPhoto::getRecordId)));
    }

    private record Photos(Map<Long, List<MadeDexRecordPhoto>> byRecord) {

        MadeDexRecordPhoto firstOf(Long recordId) {
            List<MadeDexRecordPhoto> photos = byRecord.get(recordId);
            return photos == null || photos.isEmpty() ? null : photos.getFirst();
        }

        String firstKeyOf(Long recordId) {
            MadeDexRecordPhoto first = firstOf(recordId);
            return first == null ? null : first.getImageKey();
        }
    }
}
