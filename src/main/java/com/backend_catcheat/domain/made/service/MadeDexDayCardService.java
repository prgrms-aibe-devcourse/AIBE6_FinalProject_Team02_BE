package com.backend_catcheat.domain.made.service;

import com.backend_catcheat.domain.auth.entity.User;
import com.backend_catcheat.domain.auth.repository.UserRepository;
import com.backend_catcheat.domain.made.dto.MadeDexDayCardAuthorDTO;
import com.backend_catcheat.domain.made.dto.MadeDexDayCardCalendarDTO;
import com.backend_catcheat.domain.made.dto.MadeDexDayCardDTO;
import com.backend_catcheat.domain.made.dto.MadeDexDayCardItemDTO;
import com.backend_catcheat.domain.made.dto.MadeDexDayCardParticipantDTO;
import com.backend_catcheat.domain.made.dto.MadeDexDayCardPhotoDTO;
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
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
                loaded.members(),
                slots,
                buildParticipants(records, byAuthor, loaded),
                buildStats(records, loaded));
    }

    /** 캘린더 마커 */
    public MadeDexDayCardCalendarDTO findCalendar(
            Long userId,
            Long madeDexId,
            int year,
            int month
    ) {
        madeDexFinder.readable(userId, madeDexId);

        if (month < 1 || month > 12 || year < 1970 || year > 9999) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        YearMonth target = YearMonth.of(year, month);
        List<Integer> daysWithRecords = madeDexRecordRepository
                .findLoggedOnBetween(madeDexId, target.atDay(1), target.atEndOfMonth()).stream()
                .map(LocalDate::getDayOfMonth)
                .sorted()
                .toList();

        return new MadeDexDayCardCalendarDTO(year, month, daysWithRecords);
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
     * 작성자(가입 순) → 기록 순 → 사진 순
     */
    private List<MadeDexDayCardItemDTO> buildItems(
            List<MadeDexRecord> slotRecords,
            Comparator<MadeDexRecord> byAuthor,
            Loaded loaded
    ) {
        return slotRecords.stream().sorted(byAuthor)
                .map(record -> new MadeDexDayCardItemDTO(
                        record.getId(),
                        loaded.authorOf(record.getAuthorId()),
                        photos(loaded.photosOf(record.getId()))))
                // 사진이 없는 기록은 놓을 것이 없음
                .filter(item -> !item.photos().isEmpty())
                .toList();
    }

    private List<MadeDexDayCardPhotoDTO> photos(List<MadeDexRecordPhoto> photos) {
        return photos.stream()
                .map(photo -> new MadeDexDayCardPhotoDTO(
                        photo.getId(),
                        blankToNull(photo.getCaption()),
                        s3PresignedUrlService.createDownloadUrl(photo.getImageKey())))
                .toList();
    }

    /**
     * 대표 사진 지정 — 고른 사진을 첫 장으로 옮긴다.
     * 별도 컬럼을 두지 않고 순서를 바꾼다. "첫 장이 대표"라는 약속이 저절로 지켜지고,
     * 카드에 뜨는 글도 그 사진의 caption으로 함께 따라온다.
     */
    @Transactional
    public void changeCover(
            Long userId,
            Long madeDexId,
            Long recordId,
            Long photoId
    ) {
        MadeDexRecord record = authoredRecord(userId, madeDexId, recordId);

        List<MadeDexRecordPhoto> photos = madeDexRecordPhotoRepository
                .findByRecordIdOrderBySortOrderAsc(record.getId());
        if (photoId == null || photos.stream().noneMatch(photo -> photo.getId().equals(photoId))) {
            throw new CustomException(ErrorCode.MADE_DEX_RECORD_PHOTO_NOT_FOUND);
        }

        // 고른 장만 맨 앞으로. 나머지는 원래 상대 순서를 지킨다
        int order = 0;
        for (MadeDexRecordPhoto photo : photos) {
            if (photo.getId().equals(photoId)) {
                photo.moveTo(0);
            } else {
                photo.moveTo(++order);
            }
        }
    }

    /** 그룹장도 남의 기록은 건드리지 못한다 — 기록 수정과 같은 규칙 */
    private MadeDexRecord authoredRecord(
            Long userId,
            Long madeDexId,
            Long recordId
    ) {
        madeDexFinder.active(madeDexId);
        if (!madeDexMemberRepository.existsByMadeDexIdAndUserId(madeDexId, userId)) {
            throw new CustomException(ErrorCode.MADE_DEX_NOT_MEMBER);
        }

        MadeDexRecord record = madeDexRecordRepository.findActiveByIdForUpdate(recordId)
                .orElseThrow(() -> new CustomException(ErrorCode.MADE_DEX_RECORD_NOT_FOUND));
        // 다른 그룹의 기록 id를 넣어도 남의 식탁이 드러나지 않는다
        if (!record.belongsTo(madeDexId)) {
            throw new CustomException(ErrorCode.MADE_DEX_RECORD_NOT_FOUND);
        }
        if (!record.isAuthor(userId)) {
            throw new CustomException(ErrorCode.MADE_DEX_RECORD_NOT_AUTHOR);
        }
        return record;
    }

    /** 담긴 사람
     * 작성자 순서대로, 담은 사진 수와 붙인 글
     * captions는 사진 순서 그대로 */
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

        // 가입 순
        List<Long> memberIds = madeDexMemberRepository.findByMadeDexIdOrderByJoinedAtAscIdAsc(madeDexId).stream()
                .map(MadeDexMember::getUserId)
                .distinct()
                .toList();
        Map<Long, Integer> rankByUser = new HashMap<>();
        for (int i = 0; i < memberIds.size(); i++) {
            rankByUser.put(memberIds.get(i), i);
        }

        // 이미 나간 사람의 기록도 남아 있으므로 멤버와 작성자를 합쳐서 조회한다
        List<Long> userIds = Stream.concat(memberIds.stream(), records.stream().map(MadeDexRecord::getAuthorId))
                .distinct()
                .toList();
        Map<Long, User> userById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        // 사람 표시는 여기서 한 번씩만 만들어 재사용
        Map<Long, MadeDexDayCardAuthorDTO> authorById = userIds.stream()
                .collect(Collectors.toMap(Function.identity(), id -> author(id, userId, userById)));

        return new Loaded(photos, rankByUser, authorById, memberIds);
    }

    private record Loaded(Map<Long, List<MadeDexRecordPhoto>> photosByRecord,
                          Map<Long, Integer> rankByUser,
                          Map<Long, MadeDexDayCardAuthorDTO> authorById,
                          List<Long> memberIds) {

        /** 가입 순 참여자 전원 */
        List<MadeDexDayCardAuthorDTO> members() {
            return memberIds.stream().map(this::authorOf).toList();
        }

        List<MadeDexRecordPhoto> photosOf(Long recordId) {
            return photosByRecord.getOrDefault(recordId, List.of());
        }

        MadeDexDayCardAuthorDTO authorOf(Long authorId) {
            return authorById.get(authorId);
        }
    }
}
