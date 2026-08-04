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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ChallengeParticipationService {
    private static final double LOCATION_RADIUS_M = 200; // 위치 인증 허용 반경

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
    public UnlockResponseDTO unlock(Long userId, Long challengeDexId, Long slotId, String imageKey,
                                    Double lat, Double lng){
        //인증 사진 없이 해금 방지
        if (imageKey == null || imageKey.isBlank()) {
            throw new CustomException(ErrorCode.CHALLENGE_UNLOCK_IMAGE_REQUIRED);
        }
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

        //위치 인증 챌릱지면 현재 위치가 목표 반경 내인지 확인
        ChallengeDex dex = challengeDexRepository.findByIdAndDeletedAtIsNull(challengeDexId)
                .orElseThrow(()-> new CustomException(ErrorCode.CHALLENGE_NOT_FOUND));
        if (dex.getVerifyType() == VerifyType.LOCATION){
            throw new CustomException(ErrorCode.CHALLENGE_SLOT_LOCATION_REQUIRED);
        }
        if (distanceMeters(lat, lng, slot.getLat(), slot.getLat()) > LOCATION_RADIUS_M){
            throw new CustomException(ErrorCode.CHALLENGE_LOCATION_TOO_FAR);
        }

        try {
            unlockRepository.save(ChallengeUnlock.of(participant.getId(), slotId, imageKey));
            unlockRepository.flush();
        } catch (DataIntegrityViolationException e) {
            //존재 확인과 저장 사이 동시 요청으로 유니크 제약 위반 시 → 중복 인증으로 처리
            throw new CustomException(ErrorCode.CHALLENGE_SLOT_ALREADY_UNLOCKED);
        }

        long unlocked = unlockRepository.countByChallengeParticipantId(participant.getId());
        long total = slotRepository.countByChallengeDexId(challengeDexId);
        //모든 슬롯을 인증했으면 완료 처리
        if(unlocked >= total && !participant.isCompleted()){
            participant.complete();

            //여기에 뱃지 지급 코드 넣으시면 됩니다.

        }
        return new UnlockResponseDTO(unlocked, total, participant.isCompleted());

    }

    private static double distanceMeters(double lat1, double lng1, double lat2, double lng2){
        double R = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * R * Math.asin(Math.sqrt(a));
    }


}
