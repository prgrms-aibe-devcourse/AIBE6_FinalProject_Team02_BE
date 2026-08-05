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
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import com.backend_catcheat.global.s3.S3PresignedUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChallengeService {
    private static final int MIN_SLOTS = 5;

    private final UserRepository userRepository;
    private final ChallengeDexRepository challengeDexRepository;
    private final ChallengeDexSlotRepository slotRepository;
    private final ChallengeUnlockRepository unlockRepository;
    private final ChallengeParticipantRepository participantRepository;
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
        VerifyType verifyType = req.verifyType() != null ? req.verifyType() : VerifyType.FOOD;

        ChallengeDex dex = challengeDexRepository.save(ChallengeDex.builder()
                .ownerId(ownerId)
                .name(req.name().trim())
                .description(req.description())
                .challengeType(req.challengeType())
                .periodType(req.periodType())
                .verifyType(verifyType)
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
        // 위치 인증 챌린지는 모든 목표에 좌표가 필수
        if (req.verifyType() == VerifyType.LOCATION
                && req.slots().stream().anyMatch(s -> s.lat() == null || s.lng() == null)) {
            throw new CustomException(ErrorCode.CHALLENGE_SLOT_LOCATION_REQUIRED);
        }
    }

    //챌린지 탐색
    //챌린지 탐색
    @Transactional(readOnly = true)
    public List<ChallengeSummaryDTO> getChallenges(ChallengeListStatus status){
        LocalDateTime now = LocalDateTime.now();
        List<ChallengeDex> list = (status == ChallengeListStatus.FINISHED)
                ? challengeDexRepository.findFinished(now)
                : challengeDexRepository.findOngoing(now);
        return toSummaries(list);
    }

    //내 챌린지 (개설한 / 참여 중 / 완료한)
    @Transactional(readOnly = true)
    public List<ChallengeSummaryDTO> getMyChallenges(Long userId, MyChallengeRelation relation){
        List<ChallengeDex> list = switch (relation) {
            case CREATED -> challengeDexRepository.findByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId);
            case JOINED -> loadByParticipants(participantRepository.findByUserIdAndCompletedAtIsNull(userId));
            case COMPLETED -> loadByParticipants(participantRepository.findByUserIdAndCompletedAtIsNotNull(userId));
        };
        return toSummaries(list);
    }

    private List<ChallengeDex> loadByParticipants(List<ChallengeParticipant> participants){
        List<Long> ids = participants.stream().map(ChallengeParticipant::getChallengeDexId).toList();
        return ids.isEmpty() ? List.of() : challengeDexRepository.findByIdInAndDeletedAtIsNull(ids);
    }

    //챌린지 목록 → 요약 DTO (참여자 수 한 번에 집계, N+1 방지)
    private List<ChallengeSummaryDTO> toSummaries(List<ChallengeDex> list){
        List<Long> ids = list.stream().map(ChallengeDex::getId).toList();
        Map<Long, Long> countByDex = ids.isEmpty() ? Map.of()
                : participantRepository.countByChallengeDexIdIn(ids).stream()
                  .collect(Collectors.toMap(
                          ChallengeParticipantRepository.ParticipantCount::getDexId,
                          ChallengeParticipantRepository.ParticipantCount::getCnt));
        return list.stream()
                .map(c -> toSummary(c, countByDex.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    private ChallengeSummaryDTO toSummary(ChallengeDex c, long participants){
        return new ChallengeSummaryDTO(
                c.getId(),
                c.getName(),
                c.getDescription(),
                c.getChallengeType(),
                c.getPeriodType(),
                c.getStartsAt(),
                c.getEndsAt(),
                participants
        );
    }

    @Transactional(readOnly = true)
    public ChallengeDetailResponseDTO getDetail(Long userId, Long challengeDexId){
        ChallengeDex dex = challengeDexRepository.findByIdAndDeletedAtIsNull(challengeDexId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_NOT_FOUND));
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
                                    mine != null ? mine.getUnlockedAt() : null);
                        })
                        .toList();

        return new ChallengeDetailResponseDTO(
                dex.getId(), dex.getName(), dex.getDescription(),
                dex.getChallengeType(), dex.getPeriodType(), dex.getVerifyType(),
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