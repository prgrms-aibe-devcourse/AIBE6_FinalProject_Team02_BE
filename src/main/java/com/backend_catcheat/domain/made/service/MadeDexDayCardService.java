package com.backend_catcheat.domain.made.service;

import com.backend_catcheat.domain.auth.entity.User;
import com.backend_catcheat.domain.auth.repository.UserRepository;
import com.backend_catcheat.domain.made.dto.MadeDexDayCardAuthorDTO;
import com.backend_catcheat.domain.made.dto.MadeDexDayCardDTO;
import com.backend_catcheat.domain.made.dto.MadeDexDayCardItemDTO;
import com.backend_catcheat.domain.made.dto.MadeDexDayCardParticipantDTO;
import com.backend_catcheat.domain.made.dto.MadeDexDayCardSlotDTO;
import com.backend_catcheat.domain.made.dto.MadeDexDayCardStatsDTO;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 하루 카드 뷰
 * 같은 하루 데이터를 "식탁"(feed)의 사람 단위 카드가 아니라 끼니 층 + 음식 사진으로 표현
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MadeDexDayCardService {
    private final MadeDexSlotRepository madeDexSlotRepository;
    private final MadeDexMemberRepository madeDexMemberRepository;
    private final MadeDexRecordRepository madeDexRecordRepository;
    private final MadeDexRecordPhotoRepository madeDexRecordPhotoRepository;
    private final MadeDexFinder madeDexFinder;
    private final UserRepository userRepository;
    private final S3PresignedUrlService s3PresignedUrlService;
    private final Clock clock;

    /** 오늘의 하루 카드를 조회 */
    public MadeDexDayCardDTO findDayCard(
            Long userId,
            Long madeDexId,
            LocalDate date
    ) {
        madeDexFinder.readable(userId, madeDexId);

        LocalDate today = LocalDate.now(clock.withZone(TimeConfig.SERVICE_ZONE));
        LocalDate loggedOn = date == null ? today : date;
        if (loggedOn.isAfter(today)) {
            throw new CustomException(ErrorCode.MADE_DEX_RECORD_FUTURE_DATE);
        }

        List<MadeDexRecord> records = madeDexRecordRepository
                .findByMadeDexIdAndLoggedOnAndDeletedAtIsNullOrderByCreatedAtAsc(madeDexId, loggedOn);

        Loaded loaded = load(userId, madeDexId, records);
        Comparator<MadeDexRecord> byAuthor =
                Comparator.comparingInt(record -> authorRank(record.getAuthorId(), loaded.rankByUser()));
        Map<Long, List<MadeDexRecord>> recordsBySlot = records.stream()
                .collect(Collectors.groupingBy(MadeDexRecord::getSlotId));

        List<MadeDexDayCardSlotDTO> slots = visibleSlots(madeDexId, records).stream()
                .map(slot -> new MadeDexDayCardSlotDTO(
                        slot.getId(),
                        slot.getName(),
                        slot.getSortOrder(),
                        slot.isHidden(),
                        buildItems(recordsBySlot.getOrDefault(slot.getId(), List.of()), byAuthor, loaded)))
                .toList();

        return new MadeDexDayCardDTO(
                loggedOn,
                slots,
                buildParticipants(records, byAuthor, loaded),
                buildStats(records, loaded));
    }

    /** 숨긴 끼니라도 그날 기록이 있으면 층으로 남김 */
    private List<MadeDexSlot> visibleSlots(
            Long madeDexId,
            List<MadeDexRecord> records
    ) {
        Set<Long> usedSlotIds = records.stream().map(MadeDexRecord::getSlotId).collect(Collectors.toSet());
        return madeDexSlotRepository.findByMadeDexIdOrderBySortOrderAscIdAsc(madeDexId).stream()
                .filter(slot -> !slot.isHidden() || usedSlotIds.contains(slot.getId()))
                .toList();
    }

    /** 한 층의 음식 사진 아이템
     * 작성자(가입 순) → 기록 순 → 사진 순 */
    private List<MadeDexDayCardItemDTO> buildItems(
            List<MadeDexRecord> slotRecords,
            Comparator<MadeDexRecord> byAuthor,
            Loaded loaded
    ) {
        return slotRecords.stream().sorted(byAuthor)
                .flatMap(record -> loaded.photosOf(record.getId()).stream()
                        .map(photo -> new MadeDexDayCardItemDTO(
                                blankToNull(photo.getCaption()),
                                s3PresignedUrlService.createDownloadUrl(photo.getImageKey()),
                                loaded.authorOf(record.getAuthorId()))))
                .toList();
    }

    /** 담긴 사람
     * 작성자 순서대로, 담은 사진 수와 붙인 글(있는 것만) */
    private List<MadeDexDayCardParticipantDTO> buildParticipants(
            List<MadeDexRecord> records,
            Comparator<MadeDexRecord> byAuthor,
            Loaded loaded
    ) {
        Map<Long, List<MadeDexRecord>> byAuthorId = records.stream()
                .collect(Collectors.groupingBy(MadeDexRecord::getAuthorId));
        return records.stream().sorted(byAuthor)
                .map(MadeDexRecord::getAuthorId)
                .distinct()
                .map(authorId -> {
                    List<MadeDexRecord> mine = byAuthorId.getOrDefault(authorId, List.of());
                    int count = mine.stream().mapToInt(record -> loaded.photosOf(record.getId()).size()).sum();
                    List<String> captions = mine.stream()
                            .flatMap(record -> loaded.photosOf(record.getId()).stream())
                            .map(MadeDexRecordPhoto::getCaption)
                            .map(this::blankToNull)
                            .filter(caption -> caption != null)
                            .distinct()
                            .toList();
                    MadeDexDayCardAuthorDTO author = loaded.authorOf(authorId);
                    return new MadeDexDayCardParticipantDTO(
                            author.userId(), author.nickname(), author.profileImageUrl(), author.me(),
                            count, captions);
                })
                .toList();
    }

    private MadeDexDayCardStatsDTO buildStats(
            List<MadeDexRecord> records,
            Loaded loaded
    ) {
        int foodCount = records.stream().mapToInt(record -> loaded.photosOf(record.getId()).size()).sum();
        int participantCount = (int) records.stream().map(MadeDexRecord::getAuthorId).distinct().count();
        int recordedSlotCount = (int) records.stream().map(MadeDexRecord::getSlotId).distinct().count();
        return new MadeDexDayCardStatsDTO(foodCount, participantCount, recordedSlotCount);
    }

    // 작성자당 한 번만 제작하여 재사용
    private MadeDexDayCardAuthorDTO author(
            Long authorId,
            Long userId,
            Map<Long, User> userById
    ) {
        User user = userById.get(authorId);
        return new MadeDexDayCardAuthorDTO(
                authorId,
                user == null ? null : user.getNickname(),
                user == null ? null : s3PresignedUrlService.createDownloadUrl(user.getProfileImageKey()),
                authorId.equals(userId));
    }

    // 로그잇 가입 순으로 배치
    private int authorRank(
            Long authorId,
            Map<Long, Integer> rankByUser
    ) {
        return rankByUser.getOrDefault(authorId, Integer.MAX_VALUE);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private Loaded load(Long userId, Long madeDexId, List<MadeDexRecord> records) {
        List<Long> recordIds = records.stream().map(MadeDexRecord::getId).toList();
        Map<Long, List<MadeDexRecordPhoto>> photos = recordIds.isEmpty() ? Map.of()
                : madeDexRecordPhotoRepository.findByRecordIdInOrderBySortOrderAsc(recordIds).stream()
                        .collect(Collectors.groupingBy(MadeDexRecordPhoto::getRecordId));

        // 순서 보존은 필요 없이 rank는 getOrDefault 조회에만 쓰인다. 값은 인덱스 그대로, 중복 userId는 첫 등장만 채택
        List<MadeDexMember> members = madeDexMemberRepository.findByMadeDexIdOrderByJoinedAtAscIdAsc(madeDexId);
        Map<Long, Integer> rankByUser = new HashMap<>();
        for (int i = 0; i < members.size(); i++) {
            rankByUser.putIfAbsent(members.get(i).getUserId(), i);
        }

        List<Long> authorIds = records.stream().map(MadeDexRecord::getAuthorId).distinct().toList();
        Map<Long, User> userById = userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        // 작성자 표시는 여기서 한 번씩만 만들어 재사용
        Map<Long, MadeDexDayCardAuthorDTO> authorById = authorIds.stream()
                .collect(Collectors.toMap(Function.identity(), authorId -> author(authorId, userId, userById)));

        return new Loaded(photos, rankByUser, authorById);
    }

    private record Loaded(Map<Long, List<MadeDexRecordPhoto>> photosByRecord,
                          Map<Long, Integer> rankByUser,
                          Map<Long, MadeDexDayCardAuthorDTO> authorById) {

        List<MadeDexRecordPhoto> photosOf(Long recordId) {
            return photosByRecord.getOrDefault(recordId, List.of());
        }

        MadeDexDayCardAuthorDTO authorOf(Long authorId) {
            return authorById.get(authorId);
        }
    }
}
