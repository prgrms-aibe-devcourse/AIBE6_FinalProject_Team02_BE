package com.backend_catcheat.domain.challenge.service;

import com.backend_catcheat.domain.challenge.entity.ChallengeDex;
import com.backend_catcheat.domain.challenge.entity.ChallengeParticipant;
import com.backend_catcheat.domain.challenge.entity.PeriodType;
import com.backend_catcheat.domain.challenge.repository.ChallengeDexRepository;
import com.backend_catcheat.domain.challenge.repository.ChallengeParticipantRepository;
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
}
