package com.backend_catcheat.domain.challenge.service;

import com.backend_catcheat.domain.challenge.entity.ChallengeDex;
import com.backend_catcheat.domain.challenge.entity.ChallengeDexSlot;
import com.backend_catcheat.domain.challenge.entity.ChallengeParticipant;
import com.backend_catcheat.domain.challenge.entity.ChallengeType;
import com.backend_catcheat.domain.challenge.entity.PeriodType;
import com.backend_catcheat.domain.challenge.dto.UnlockResponseDTO;
import com.backend_catcheat.domain.challenge.repository.ChallengeDexRepository;
import com.backend_catcheat.domain.challenge.repository.ChallengeDexSlotRepository;
import com.backend_catcheat.domain.challenge.repository.ChallengeParticipantRepository;
import com.backend_catcheat.domain.challenge.repository.ChallengeUnlockRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChallengeParticipationService 단위 테스트 — 참여(join) + 해금(unlock).
 */
@ExtendWith(MockitoExtension.class)
class ChallengeParticipationServiceTest {

    @Mock
    ChallengeDexRepository challengeDexRepository;
    @Mock
    ChallengeParticipantRepository participantRepository;
    @Mock
    ChallengeDexSlotRepository slotRepository;
    @Mock
    ChallengeUnlockRepository unlockRepository;

    @InjectMocks
    ChallengeParticipationService service;

    private static final Long DEX_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final Long SLOT_ID = 7L;

    private ChallengeDex permanentDex() {
        return ChallengeDex.builder()
                .ownerId(99L).name("챌린지")
                .challengeType(ChallengeType.COLLECTION).periodType(PeriodType.PERMANENT)
                .startsAt(LocalDateTime.now().minusDays(1)).event(false)
                .build();
    }

    // ===== 참여 =====

    @Test
    @DisplayName("정상 참여하면 참여자가 저장된다")
    void join_success() {
        when(challengeDexRepository.findByIdAndDeletedAtIsNull(DEX_ID))
                .thenReturn(Optional.of(permanentDex()));
        when(participantRepository.existsByChallengeDexIdAndUserId(DEX_ID, USER_ID)).thenReturn(false);
        when(participantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> service.join(USER_ID, DEX_ID)).doesNotThrowAnyException();
        verify(participantRepository).save(any());
    }

    @Test
    @DisplayName("없는 챌린지에 참여하면 CHALLENGE_NOT_FOUND")
    void join_notFound() {
        when(challengeDexRepository.findByIdAndDeletedAtIsNull(DEX_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.join(USER_ID, DEX_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CHALLENGE_NOT_FOUND));
    }

    @Test
    @DisplayName("이미 참여했으면 CHALLENGE_ALREADY_JOINED")
    void join_alreadyJoined() {
        when(challengeDexRepository.findByIdAndDeletedAtIsNull(DEX_ID))
                .thenReturn(Optional.of(permanentDex()));
        when(participantRepository.existsByChallengeDexIdAndUserId(DEX_ID, USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.join(USER_ID, DEX_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CHALLENGE_ALREADY_JOINED));
    }

    @Test
    @DisplayName("종료된 기간 한정 챌린지는 CHALLENGE_ENDED")
    void join_ended() {
        ChallengeDex ended = ChallengeDex.builder()
                .ownerId(99L).name("끝난 챌린지")
                .challengeType(ChallengeType.COLLECTION).periodType(PeriodType.LIMITED)
                .startsAt(LocalDateTime.now().minusDays(10))
                .endsAt(LocalDateTime.now().minusDays(1))   // 이미 종료
                .event(false).build();
        when(challengeDexRepository.findByIdAndDeletedAtIsNull(DEX_ID)).thenReturn(Optional.of(ended));

        assertThatThrownBy(() -> service.join(USER_ID, DEX_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CHALLENGE_ENDED));
    }

    // ===== 해금 =====

    private void stubJoinedAndSlot() {
        when(participantRepository.findByChallengeDexIdAndUserId(DEX_ID, USER_ID))
                .thenReturn(Optional.of(ChallengeParticipant.join(DEX_ID, USER_ID)));
        ChallengeDexSlot slot = ChallengeDexSlot.builder()
                .challengeDexId(DEX_ID).foodName("음식").slotOrder(0).build();
        when(slotRepository.findById(SLOT_ID)).thenReturn(Optional.of(slot));
        when(unlockRepository.existsByChallengeParticipantIdAndSlotId(any(), eq(SLOT_ID))).thenReturn(false);
        when(challengeDexRepository.findByIdAndDeletedAtIsNull(DEX_ID)).thenReturn(Optional.of(permanentDex()));
    }

    @Test
    @DisplayName("일부만 해금하면 완료되지 않는다")
    void unlock_notCompleteYet() {
        stubJoinedAndSlot();
        when(unlockRepository.countByChallengeParticipantId(any())).thenReturn(1L);
        when(slotRepository.countByChallengeDexId(DEX_ID)).thenReturn(5L);

        UnlockResponseDTO res = service.unlock(USER_ID, DEX_ID, SLOT_ID, "img-key", null, null);

        assertThat(res.unlockedCount()).isEqualTo(1);
        assertThat(res.totalSlots()).isEqualTo(5);
        assertThat(res.completed()).isFalse();
        verify(unlockRepository).save(any());
    }

    @Test
    @DisplayName("전 슬롯을 해금하면 완료 처리된다")
    void unlock_completesWhenAllUnlocked() {
        stubJoinedAndSlot();
        when(unlockRepository.countByChallengeParticipantId(any())).thenReturn(5L);
        when(slotRepository.countByChallengeDexId(DEX_ID)).thenReturn(5L);

        UnlockResponseDTO res = service.unlock(USER_ID, DEX_ID, SLOT_ID, "img-key", null, null);

        assertThat(res.completed()).isTrue();
    }

    @Test
    @DisplayName("인증 사진(imageKey)이 비어 있으면 CHALLENGE_UNLOCK_IMAGE_REQUIRED")
    void unlock_blankImageKey() {
        assertThatThrownBy(() -> service.unlock(USER_ID, DEX_ID, SLOT_ID, "  ", null, null))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CHALLENGE_UNLOCK_IMAGE_REQUIRED));
        assertThatThrownBy(() -> service.unlock(USER_ID, DEX_ID, SLOT_ID, null, null, null))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CHALLENGE_UNLOCK_IMAGE_REQUIRED));
    }

    @Test
    @DisplayName("참여하지 않았으면 CHALLENGE_NOT_JOINED")
    void unlock_notJoined() {
        when(participantRepository.findByChallengeDexIdAndUserId(DEX_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unlock(USER_ID, DEX_ID, SLOT_ID, "img-key", null, null))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CHALLENGE_NOT_JOINED));
    }

    @Test
    @DisplayName("이미 인증한 슬롯이면 CHALLENGE_SLOT_ALREADY_UNLOCKED")
    void unlock_alreadyUnlocked() {
        when(participantRepository.findByChallengeDexIdAndUserId(DEX_ID, USER_ID))
                .thenReturn(Optional.of(ChallengeParticipant.join(DEX_ID, USER_ID)));
        ChallengeDexSlot slot = ChallengeDexSlot.builder()
                .challengeDexId(DEX_ID).foodName("음식").slotOrder(0).build();
        when(slotRepository.findById(SLOT_ID)).thenReturn(Optional.of(slot));
        when(unlockRepository.existsByChallengeParticipantIdAndSlotId(any(), eq(SLOT_ID))).thenReturn(true);

        assertThatThrownBy(() -> service.unlock(USER_ID, DEX_ID, SLOT_ID, "img-key", null, null))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CHALLENGE_SLOT_ALREADY_UNLOCKED));
    }
}
