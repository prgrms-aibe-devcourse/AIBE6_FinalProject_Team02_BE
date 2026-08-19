package com.backend_catcheat.domain.challenge.service;

import com.backend_catcheat.domain.auth.entity.User;
import com.backend_catcheat.domain.auth.repository.UserRepository;
import com.backend_catcheat.domain.challenge.dto.*;
import com.backend_catcheat.domain.challenge.dto.ChallengeCreateRequestDTO.SlotInput;
import com.backend_catcheat.domain.challenge.entity.*;
import com.backend_catcheat.domain.challenge.repository.*;
import com.backend_catcheat.global.common.PageResponse;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import com.backend_catcheat.global.s3.S3PresignedUrlService;
import com.backend_catcheat.global.event.S3ObjectUnusedEvent;
import org.springframework.context.ApplicationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

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
    private final ApplicationEventPublisher eventPublisher;
    private static final int SEARCH_LIMIT = 20;

    //개설권 조회
    @Transactional
    public CreationTicketResponseDTO getRemainingTickets(Long userId){
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return new CreationTicketResponseDTO(user.remainingChallengeTickets(currentYearMonth()));
    }

    //챌린지 삭제 (개설자만). FK CASCADE로 슬롯·참여자·해금·조회수·리뷰·좋아요 자동 정리
    @Transactional
    public void delete(Long userId, Long challengeDexId){
        ChallengeDex dex = challengeDexRepository.findById(challengeDexId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_NOT_FOUND));
        if (!dex.getOwnerId().equals(userId)) {
            throw new CustomException(ErrorCode.CHALLENGE_NOT_OWNER);
        }

        // 삭제 전에 S3 key 수집 — CASCADE로 자식이 사라지면 key를 못 읽는다
        List<String> imageKeys = new ArrayList<>();
        if (dex.getImageKey() != null) {
            imageKeys.add(dex.getImageKey());
        }
        imageKeys.addAll(slotRepository.findImageKeysByChallengeDexId(challengeDexId));
        imageKeys.addAll(unlockRepository.findImageKeysByChallengeDexId(challengeDexId));

        // DB 삭제 — 나머지는 FK ON DELETE CASCADE가 정리
        challengeDexRepository.delete(dex);

        // 커밋 이후 S3 객체 + 업로드 발급기록 정리(S3ObjectCleanupListener)
        imageKeys.forEach(key -> eventPublisher.publishEvent(new S3ObjectUnusedEvent(key)));
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
                .imageKey(req.imageKey())
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
                    .storeName(s.storeName())
                    .description(s.description())
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
        if (req.periodType() == null) {
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

        if (req.slots().stream().anyMatch(s -> s.lat() == null || s.lng() == null)) {
            throw new CustomException(ErrorCode.CHALLENGE_SLOT_LOCATION_REQUIRED);
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

    @Transactional(readOnly = true)
    public PageResponse<ChallengeSummaryDTO> search(Long userId, String keyword, int page, int size){
        if (!StringUtils.hasText(keyword)) {
            return new PageResponse<>(List.of(), Math.max(0, page), size, 0, 0, false);
        }
        List<ChallengeDex> found = challengeDexRepository.searchByNameContaining(keyword.trim());
        return paginate(userId, found, null, page, size);   // 참여자수·참여여부·hasNext 계산까지 재사용
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
                        0, 0,
                        scoreByDex == null ? null : scoreByDex.getOrDefault(c.getId(), 0L),
                        joinedIds.contains(c.getId())))
                .toList();

        int totalPages = (int) Math.ceil((double) total / safeSize);
        boolean hasNext = safePage + 1 < totalPages;
        return new PageResponse<>(content, safePage, safeSize, total, totalPages, hasNext);
    }

    //내 챌린지 (개설한 / 참여 중 / 완료한) — 내 진행도(해금 수/전체) 포함
    @Transactional(readOnly = true)
    public List<ChallengeSummaryDTO> getMyChallenges(Long userId, MyChallengeRelation relation){
        List<ChallengeDex> list = switch (relation) {
            case CREATED -> challengeDexRepository.findByOwnerIdOrderByCreatedAtDesc(userId);
            case JOINED -> loadByParticipants(participantRepository.findByUserIdAndCompletedAtIsNull(userId));
            case COMPLETED -> loadByParticipants(participantRepository.findByUserIdAndCompletedAtIsNotNull(userId));
        };

        List<Long> ids = list.stream().map(ChallengeDex::getId).toList();
        Map<Long, Long> countByDex = ids.isEmpty() ? Map.of()
                : participantRepository.countByChallengeDexIdIn(ids).stream()
                        .collect(Collectors.toMap(
                                ChallengeParticipantRepository.ParticipantCount::getDexId,
                                ChallengeParticipantRepository.ParticipantCount::getCnt));

        return list.stream()
                .map(c -> {
                    int totalSlots = (int) slotRepository.countByChallengeDexId(c.getId());
                    int unlocked = participantRepository
                            .findByChallengeDexIdAndUserId(c.getId(), userId)
                            .map(p -> (int) unlockRepository.countByChallengeParticipantId(p.getId()))
                            .orElse(0);
                    return toSummary(c, countByDex.getOrDefault(c.getId(), 0L),
                            totalSlots, unlocked, null, false);
                })
                .toList();
    }

    private List<ChallengeDex> loadByParticipants(List<ChallengeParticipant> participants){
        List<Long> ids = participants.stream().map(ChallengeParticipant::getChallengeDexId).toList();
        return ids.isEmpty() ? List.of() : challengeDexRepository.findByIdIn(ids);
    }

    // 챌린지 요약 DTO — 진행도(내 챌린지) + 랭킹/참여여부(탐색) 모두 지원
    private ChallengeSummaryDTO toSummary(
            ChallengeDex c,
            long participants,
            int totalSlots,
            int unlockedCount,
            Long rankScore,
            boolean joined
    ){
        return new ChallengeSummaryDTO(
                c.getId(),
                c.getName(),
                c.getDescription(),
                c.getPeriodType(),
                c.getStartsAt(),
                c.getEndsAt(),
                participants,
                totalSlots,
                unlockedCount,
                rankScore,
                joined,
                s3PresignedUrlService.createDownloadUrl(c.getImageKey())
        );
    }

    @Transactional
    public ChallengeDetailResponseDTO getDetail(Long userId, Long challengeDexId){
        ChallengeDex dex = challengeDexRepository.findById(challengeDexId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_NOT_FOUND));
        viewDailyRepository.increment(challengeDexId);   // 상세 진입 조회수 +1(새로고침 포함)
        Optional<ChallengeParticipant> participant = participantRepository.findByChallengeDexIdAndUserId(challengeDexId, userId);

        //내 인증 기록 (슬롯별) — 해금 여부 + 내 사진/시각
        Map<Long, ChallengeUnlock> myUnlocks = participant
                .map(p -> unlockRepository.findByChallengeParticipantId(p.getId()).stream()
                        .collect(Collectors.toMap(ChallengeUnlock::getSlotId, u -> u)))
                .orElse(Map.of());

        List<ChallengeDetailResponseDTO.SlotDetail> slots =
                slotRepository.findByChallengeDexIdOrderBySlotOrderAsc(challengeDexId).stream()
                        .map(s -> {
                            ChallengeUnlock mine = myUnlocks.get(s.getId());
                            return new ChallengeDetailResponseDTO.SlotDetail(
                                    s.getId(), s.getFoodName(), s.getPlaceName(), s.getSlotOrder(),
                                    mine != null,
                                    // 개설자가 등록한 목표 사진 (미해금이면 흑백)
                                    s.getImageKey() == null ? null
                                            : s3PresignedUrlService.createDownloadUrl(s.getImageKey()),
                                    // 내가 인증한 사진 (해금 시)
                                    mine != null && mine.getImageKey() != null
                                            ? s3PresignedUrlService.createDownloadUrl(mine.getImageKey())
                                            : null,
                                    mine != null ? mine.getUnlockedAt() : null,
                                    s.getStoreName(),
                                    s.getDescription());
                        })
                        .toList();

        return new ChallengeDetailResponseDTO(
                dex.getId(), dex.getName(), dex.getDescription(),
                 dex.getPeriodType(),
                dex.getStartsAt(), dex.getEndsAt(), dex.getRewardBadgeId(),
                participantRepository.countByChallengeDexId(challengeDexId),
                participant.isPresent(),
                participant.map(ChallengeParticipant::isCompleted).orElse(false),
                s3PresignedUrlService.createDownloadUrl(dex.getImageKey()),
                slots);
    }

    //yyyymm 정수 (예: 2026년 7월 → 202607)
    private int currentYearMonth() {
        YearMonth ym = YearMonth.now();
        return ym.getYear() * 100 + ym.getMonthValue();
    }
}
