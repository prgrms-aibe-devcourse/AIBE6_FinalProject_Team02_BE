package com.backend_catcheat.domain.challenge.service;

import com.backend_catcheat.domain.auth.entity.User;
import com.backend_catcheat.domain.auth.repository.UserRepository;
import com.backend_catcheat.domain.challenge.dto.*;
import com.backend_catcheat.domain.challenge.dto.ChallengeCreateRequestDTO.SlotInput;
import com.backend_catcheat.domain.challenge.entity.*;
import com.backend_catcheat.domain.challenge.repository.ChallengeDexRepository;
import com.backend_catcheat.domain.challenge.repository.ChallengeDexSlotRepository;
import com.backend_catcheat.domain.challenge.repository.ChallengeParticipantRepository;
import com.backend_catcheat.domain.challenge.repository.ChallengeUnlockRepository;
import com.backend_catcheat.domain.challenge.repository.ChallengeViewDailyRepository;
import com.backend_catcheat.domain.challenge.repository.DexScore;
import com.backend_catcheat.global.common.PageResponse;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import com.backend_catcheat.global.s3.S3PresignedUrlService;

@Service
@RequiredArgsConstructor
public class ChallengeService {
    private static final int MIN_SLOTS = 5;
    private static final int MAX_PAGE_SIZE = 50;   // 탐색 페이지 크기 상한(과대 요청 방어)

    private final UserRepository userRepository;
    private final ChallengeDexRepository challengeDexRepository;
    private final ChallengeDexSlotRepository slotRepository;
    private final ChallengeUnlockRepository unlockRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final ChallengeViewDailyRepository viewDailyRepository;
    private final S3PresignedUrlService s3PresignedUrlService;

    //개설권 조회
    @Transactional
    public CreationTicketResponseDTO getRemainingTickets(Long userId){
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return new CreationTicketResponseDTO(user.remainingChallengeTickets(currentYearMonth()));
    }

    @Transactional
    public ChallengeCreateResponseDTO create(
            Long ownerId,
            ChallengeCreateRequestDTO req
    ){
        validate(req);
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        owner.useChallengeTicket(currentYearMonth());

        LocalDateTime startsAt = req.startsAt() != null ? req.startsAt() : LocalDateTime.now();
        LocalDateTime endsAt = req.periodType() == PeriodType.LIMITED ? req.endsAt() : null;

        ChallengeDex dex = challengeDexRepository.save(ChallengeDex.builder()
                .ownerId(ownerId)
                .name(req.name().trim())
                .description(req.description())
                .challengeType(req.challengeType())
                .periodType(req.periodType())
                .startsAt(startsAt)
                .endsAt(endsAt)
                .rewardBadgeId(req.rewardBadgeId())
                .event(false)
                .build());

        List<SlotInput> inputs = req.slots();
        List<ChallengeDexSlot> slots = new ArrayList<>();
        for(int i = 0; i < inputs.size(); i++){
            SlotInput s = inputs.get(i);
            slots.add(ChallengeDexSlot.builder()
                    .challengeDexId(dex.getId())
                    .foodName(s.foodName().trim())
                    .placeName(s.placeName())
                    .lat(s.lat())
                    .lng(s.lng())
                    .slotOrder(i)              // 입력 순서대로 표시 순서 부여
                    .imageKey(s.imageKey())    // 개설자가 등록한 목표 음식 사진(S3 key)
                    .build());
        }
        slotRepository.saveAll(slots);

        return new ChallengeCreateResponseDTO(
                dex.getId(), owner.remainingChallengeTickets(currentYearMonth())
        );
    }

    //요청 검증
    private void validate(ChallengeCreateRequestDTO req) {
        if (req.name() == null || req.name().trim().isEmpty()) {
            throw new CustomException(ErrorCode.CHALLENGE_NAME_REQUIRED);
        }
        if (req.challengeType() == null || req.periodType() == null) {
            throw new CustomException(ErrorCode.CHALLENGE_TYPE_REQUIRED);
        }
        if (req.slots() == null || req.slots().size() < MIN_SLOTS
                || req.slots().stream().anyMatch(s -> s.foodName() == null || s.foodName().trim().isEmpty())) {
            throw new CustomException(ErrorCode.CHALLENGE_SLOT_MIN_REQUIRED);
        }
        if (req.periodType() == PeriodType.LIMITED) {
            LocalDateTime start = req.startsAt() != null ? req.startsAt() : LocalDateTime.now();
            if (req.endsAt() == null || !req.endsAt().isAfter(start)) {
                throw new CustomException(ErrorCode.CHALLENGE_PERIOD_INVALID);
            }
        }
    }

    //챌린지 탐색 (정렬 + 페이지)
    @Transactional(readOnly = true)
    public PageResponse<ChallengeSummaryDTO> getChallenges(
            Long userId,
            ChallengeListStatus status,
            ChallengeSortType sort,
            int page,
            int size
    ) {
        LocalDateTime now = LocalDateTime.now();

        // 완료 탭: 랭킹 미적용, 최근 완료순만
        if (status == ChallengeListStatus.FINISHED) {
            return paginate(userId, challengeDexRepository.findFinished(now), null, page, size);
        }

        List<ChallengeDex> ongoing = challengeDexRepository.findOngoing(now); // createdAt desc
        // 최신순: 이미 정렬됨, 점수 없음
        if (sort == ChallengeSortType.LATEST) {
            return paginate(userId, ongoing, null, page, size);
        }

        // 랭킹: 지표 집계 후 점수 desc 정렬(동점은 createdAt desc 유지 — 안정 정렬)
        List<Long> ids = ongoing.stream().map(ChallengeDex::getId).toList();
        Map<Long, Long> scoreByDex = scoreMap(sort, ids, now);
        List<ChallengeDex> ranked = ongoing.stream()
                .sorted(Comparator.comparingLong(
                        (ChallengeDex c) -> scoreByDex.getOrDefault(c.getId(), 0L)).reversed())
                .toList();
        return paginate(userId, ranked, scoreByDex, page, size);
    }

    // 선택 지표의 dex별 점수 맵
    private Map<Long, Long> scoreMap(
            ChallengeSortType sort,
            List<Long> ids,
            LocalDateTime now
    ) {
        if (ids.isEmpty()) return Map.of();
        // 최근 7일 = 오늘 포함 이전 6일
        LocalDate sinceDate = now.toLocalDate().minusDays(6);
        List<DexScore> rows = switch (sort) {
            case VIEWS -> viewDailyRepository.sumRecentViewsByDexIn(ids, sinceDate);
            case PARTICIPANTS -> participantRepository.countRecentJoinsByDexIn(ids, sinceDate.atStartOfDay());
            case UNLOCKS -> unlockRepository.countRecentUnlocksByDexIn(ids, sinceDate.atStartOfDay());
            case LATEST -> List.of();
        };
        return rows.stream().collect(Collectors.toMap(DexScore::getDexId, DexScore::getScore));
    }

    // 정렬된 전체 목록을 페이지로 자르고, 페이지 슬라이스만 참여자수·참여여부 집계
    private PageResponse<ChallengeSummaryDTO> paginate(
            Long userId,
            List<ChallengeDex> sorted,
            Map<Long, Long> scoreByDex,
            int page,
            int size
    ) {
        // page/size 방어
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        int total = sorted.size();
        long fromL = (long) safePage * safeSize;   // 큰 page의 int 오버플로 방지
        int from = (int) Math.min(fromL, total);
        int to = (int) Math.min(fromL + safeSize, total);
        List<ChallengeDex> slice = sorted.subList(from, to);

        List<Long> sliceIds = slice.stream().map(ChallengeDex::getId).toList();
        Map<Long, Long> participantByDex = sliceIds.isEmpty() ? Map.of()
                : participantRepository.countByChallengeDexIdIn(sliceIds).stream()
                        .collect(Collectors.toMap(
                                ChallengeParticipantRepository.ParticipantCount::getDexId,
                                ChallengeParticipantRepository.ParticipantCount::getCnt));

        // 내 참여 여부(로그인 유저 한정)
        Set<Long> joinedIds = (userId == null || sliceIds.isEmpty()) ? Set.of()
                : new HashSet<>(participantRepository.findJoinedDexIds(userId, sliceIds));

        List<ChallengeSummaryDTO> content = slice.stream()
                .map(c -> toSummary(c,
                        participantByDex.getOrDefault(c.getId(), 0L),
                        scoreByDex == null ? null : scoreByDex.getOrDefault(c.getId(), 0L),
                        joinedIds.contains(c.getId())))
                .toList();

        int totalPages = (int) Math.ceil((double) total / safeSize);
        boolean hasNext = safePage + 1 < totalPages;
        return new PageResponse<>(content, safePage, safeSize, total, totalPages, hasNext);
    }

    private ChallengeSummaryDTO toSummary(
            ChallengeDex c,
            long participants,
            Long rankScore,
            boolean joined
    ){
        return new ChallengeSummaryDTO(
                c.getId(), c.getName(), c.getDescription(),
                c.getChallengeType(), c.getPeriodType(),
                c.getStartsAt(), c.getEndsAt(),
                participants, rankScore, joined);
    }

    @Transactional
    public ChallengeDetailResponseDTO getDetail(
            Long userId,
            Long challengeDexId
    ){
        ChallengeDex dex = challengeDexRepository.findByIdAndDeletedAtIsNull(challengeDexId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_NOT_FOUND));
        viewDailyRepository.increment(challengeDexId);   // 상세 진입 조회수 +1(새로고침 포함)
        Optional<ChallengeParticipant> participant = participantRepository.findByChallengeDexIdAndUserId(challengeDexId, userId);

        //인증한 슬롯 id들
        Set<Long> unlockedSlotIds =participant
                .map(p -> unlockRepository.findByChallengeParticipantId(p.getId()).stream()
                        .map(ChallengeUnlock::getSlotId)
                        .collect(Collectors.toSet()))
                        .orElse(Set.of());

        List<ChallengeDetailResponseDTO.SlotDetail> slots =
                slotRepository.findByChallengeDexIdOrderBySlotOrderAsc(challengeDexId).stream()
                        .map(s -> new ChallengeDetailResponseDTO.SlotDetail(
                                s.getId(), s.getFoodName(), s.getPlaceName(), s.getSlotOrder(),
                                unlockedSlotIds.contains(s.getId()),
                                // 개설자가 등록한 목표 사진 → 조회용 프리사인 URL (없으면 null)
                                s.getImageKey() == null ? null
                                        : s3PresignedUrlService.createDownloadUrl(s.getImageKey())))
                        .toList();

        return new ChallengeDetailResponseDTO(
                dex.getId(), dex.getName(), dex.getDescription(),
                dex.getChallengeType(), dex.getPeriodType(),
                dex.getStartsAt(), dex.getEndsAt(), dex.getRewardBadgeId(),
                participantRepository.countByChallengeDexId(challengeDexId),
                participant.isPresent(),
                participant.map(ChallengeParticipant::isCompleted).orElse(false),
                slots);

    }

    //yyyymm 정수 (예: 2026년 7월 → 202607)
    private int currentYearMonth() {
        YearMonth ym = YearMonth.now();
        return ym.getYear() * 100 + ym.getMonthValue();
    }
}
