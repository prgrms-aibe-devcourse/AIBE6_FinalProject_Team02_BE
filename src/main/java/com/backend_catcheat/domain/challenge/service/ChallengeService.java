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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    //챌린지 탐색
    @Transactional(readOnly = true)
    public List<ChallengeSummaryDTO> getChallenges(ChallengeListStatus status){
        LocalDateTime now = LocalDateTime.now();
        List<ChallengeDex>  list = (status == ChallengeListStatus.FINISHED)
                ? challengeDexRepository.findFinished(now)
                : challengeDexRepository.findOngoing(now);
        return list.stream().map(this::toSummary).toList();
    }

    private ChallengeSummaryDTO toSummary(ChallengeDex c){
        long paticipants = participantRepository.countByChallengeDexId(c.getId());
        return new ChallengeSummaryDTO(
                c.getId(),
                c.getName(),
                c.getDescription(),
                c.getChallengeType(),
                c.getPeriodType(),
                c.getStartsAt(),
                c.getEndsAt(),
                paticipants
        );
    }
    @Transactional(readOnly = true)
    public ChallengeDetailResponseDTO getDetail(Long userId, Long challengeDexId){
        ChallengeDex dex = challengeDexRepository.findByIdAndDeletedAtIsNull(challengeDexId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_NOT_FOUND));
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
                                unlockedSlotIds.contains(s.getId())))
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
