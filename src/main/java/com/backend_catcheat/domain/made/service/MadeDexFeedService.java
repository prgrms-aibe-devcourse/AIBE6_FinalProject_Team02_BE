package com.backend_catcheat.domain.made.service;

import com.backend_catcheat.domain.auth.entity.User;
import com.backend_catcheat.domain.auth.repository.UserRepository;
import com.backend_catcheat.domain.made.dto.MadeDexFeedCardDTO;
import com.backend_catcheat.domain.made.dto.MadeDexFeedDTO;
import com.backend_catcheat.domain.made.dto.MadeDexFeedSlotDTO;
import com.backend_catcheat.domain.made.dto.MadeDexRecordSummaryDTO;
import com.backend_catcheat.domain.made.entity.MadeDexMember;
import com.backend_catcheat.domain.made.entity.MadeDexRecord;
import com.backend_catcheat.domain.made.entity.MadeDexRecordFood;
import com.backend_catcheat.domain.made.entity.MadeDexRecordPhoto;
import com.backend_catcheat.domain.made.entity.MadeDexSlot;
import com.backend_catcheat.domain.made.repository.MadeDexMemberRepository;
import com.backend_catcheat.domain.made.repository.MadeDexRecordFoodRepository;
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
    private final MadeDexRecordFoodRepository madeDexRecordFoodRepository;
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

        LocalDate loggedOn = date == null ? LocalDate.now(clock.withZone(TimeConfig.SERVICE_ZONE)) : date;
        if (loggedOn.isAfter(LocalDate.now(clock.withZone(TimeConfig.SERVICE_ZONE)))) {
            throw new CustomException(ErrorCode.MADE_DEX_RECORD_FUTURE_DATE);
        }

        List<MadeDexRecord> records = madeDexRecordRepository
                .findByMadeDexIdAndLoggedOnAndDeletedAtIsNullOrderByCreatedAtAsc(madeDexId, loggedOn);

        List<MadeDexFeedCardDTO> emptyCards = emptyCards(madeDexId, userId);
        Map<Long, Map<Long, List<MadeDexRecordSummaryDTO>>> bySlotAndAuthor = summarize(records);

        return new MadeDexFeedDTO(loggedOn, visibleSlots(madeDexId, records).stream()
                .map(slot -> new MadeDexFeedSlotDTO(
                        slot.getId(),
                        slot.getName(),
                        slot.isHidden(),
                        cardsOf(emptyCards, bySlotAndAuthor.getOrDefault(slot.getId(), Map.of()))))
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
                            List.of());
                })
                .toList();
    }

    private List<MadeDexFeedCardDTO> cardsOf(List<MadeDexFeedCardDTO> emptyCards,
                                             Map<Long, List<MadeDexRecordSummaryDTO>> byAuthor) {
        return emptyCards.stream()
                .map(card -> new MadeDexFeedCardDTO(
                        card.userId(),
                        card.nickname(),
                        card.profileImageUrl(),
                        card.me(),
                        byAuthor.getOrDefault(card.userId(), List.of())))
                .toList();
    }

    private Map<Long, Map<Long, List<MadeDexRecordSummaryDTO>>> summarize(List<MadeDexRecord> records) {
        if (records.isEmpty()) {
            return Map.of();
        }

        List<Long> recordIds = records.stream().map(MadeDexRecord::getId).toList();
        Map<Long, List<MadeDexRecordPhoto>> photosByRecord = madeDexRecordPhotoRepository
                .findByRecordIdInOrderBySortOrderAsc(recordIds).stream()
                .collect(Collectors.groupingBy(MadeDexRecordPhoto::getRecordId));
        Map<Long, List<MadeDexRecordFood>> foodsByRecord = madeDexRecordFoodRepository
                .findByRecordIdInOrderBySortOrderAsc(recordIds).stream()
                .collect(Collectors.groupingBy(MadeDexRecordFood::getRecordId));

        return records.stream().collect(Collectors.groupingBy(
                MadeDexRecord::getSlotId,
                Collectors.groupingBy(
                        MadeDexRecord::getAuthorId,
                        Collectors.mapping(
                                record -> toSummary(record,
                                        photosByRecord.getOrDefault(record.getId(), List.of()),
                                        foodsByRecord.getOrDefault(record.getId(), List.of())),
                                Collectors.toList()))));
    }

    private MadeDexRecordSummaryDTO toSummary(MadeDexRecord record,
                                              List<MadeDexRecordPhoto> photos,
                                              List<MadeDexRecordFood> foods) {
        String thumbnailKey = photos.isEmpty() ? null : photos.getFirst().getImageKey();
        return new MadeDexRecordSummaryDTO(
                record.getId(),
                s3PresignedUrlService.createDownloadUrl(thumbnailKey),
                photos.size(),
                foods.stream().map(MadeDexRecordFood::getFoodName).toList());
    }
}
