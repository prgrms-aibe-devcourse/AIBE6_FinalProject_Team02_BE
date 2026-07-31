package com.backend_catcheat.domain.challenge.service;

import com.backend_catcheat.domain.challenge.dto.UnlockResponseDTO;
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

@Service
@RequiredArgsConstructor
public class ChallengeParticipationService {
    private final ChallengeDexRepository challengeDexRepository;
    private final ChallengeParticipantRepository  participantRepository;
    private final ChallengeDexSlotRepository slotRepository;
    private final ChallengeUnlockRepository unlockRepository;
    @Transactional
    public Long join(Long userId, Long challengeDexId){

        ChallengeDex dex = challengeDexRepository.findByIdAndDeletedAtIsNull(challengeDexId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_NOT_FOUND));

        //기간 한정인데 이미 종료시 참여 불가
        if (dex.getPeriodType() == PeriodType.LIMITED
                && dex.getEndsAt() != null
                && !dex.getEndsAt().isAfter(LocalDateTime.now())
        ) {
            throw new CustomException(ErrorCode.CHALLENGE_ENDED);
        }
        //  중복 참여 불가
        if (participantRepository.existsByChallengeDexIdAndUserId(challengeDexId, userId)) {
            throw new CustomException(ErrorCode.CHALLENGE_ALREADY_JOINED);
        }

        ChallengeParticipant participant = participantRepository.save(
                ChallengeParticipant.join(challengeDexId, userId));
        return participant.getId();
    }

    @Transactional
    public UnlockResponseDTO unlock(Long userId, Long challengeDexId, Long slotId, String imageKey){
        ChallengeParticipant participant = participantRepository
                .findByChallengeDexIdAndUserId(challengeDexId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_NOT_JOINED));
        //슬롯과 챌린지 관계 확인
        ChallengeDexSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_SLOT_NOT_FOUND));
        if(!slot.getChallengeDexId().equals(challengeDexId)){
            throw new CustomException(ErrorCode.CHALLENGE_SLOT_NOT_FOUND);
        }
        //같은 슬롯 중복 인증 방지
        if(unlockRepository.existsByChallengeParticipantIdAndSlotId(participant.getId(), slotId)) {
            throw new CustomException(ErrorCode.CHALLENGE_SLOT_ALREADY_UNLOCKED);
        }
        unlockRepository.save(ChallengeUnlock.of(participant.getId(), slotId, imageKey));

        long unlocked = unlockRepository.countByChallengeParticipantId(participant.getId());
        long total = slotRepository.countByChallengeDexId(challengeDexId);
        //모든 슬롯을 인증했으면 완료 처리
        if(unlocked >= total && !participant.isCompleted()){
            participant.complete();

            //여기에 뱃지 지급 코드 넣으시면 됩니다.

        }
        return new UnlockResponseDTO(unlocked, total, participant.isCompleted());

    }


}
