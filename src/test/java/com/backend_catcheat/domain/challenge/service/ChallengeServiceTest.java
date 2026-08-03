package com.backend_catcheat.domain.challenge.service;

import com.backend_catcheat.domain.auth.entity.Provider;
import com.backend_catcheat.domain.auth.entity.Role;
import com.backend_catcheat.domain.auth.entity.User;
import com.backend_catcheat.domain.auth.repository.UserRepository;
import com.backend_catcheat.domain.challenge.dto.ChallengeCreateRequestDTO;
import com.backend_catcheat.domain.challenge.dto.ChallengeCreateRequestDTO.SlotInput;
import com.backend_catcheat.domain.challenge.dto.ChallengeCreateResponseDTO;
import com.backend_catcheat.domain.challenge.dto.CreationTicketResponseDTO;
import com.backend_catcheat.domain.challenge.entity.ChallengeType;
import com.backend_catcheat.domain.challenge.entity.PeriodType;
import com.backend_catcheat.domain.challenge.dto.ChallengeListStatus;
import com.backend_catcheat.domain.challenge.dto.ChallengeSummaryDTO;
import com.backend_catcheat.domain.challenge.entity.ChallengeDex;
import com.backend_catcheat.domain.challenge.repository.ChallengeDexRepository;
import com.backend_catcheat.domain.challenge.repository.ChallengeDexSlotRepository;
import com.backend_catcheat.domain.challenge.repository.ChallengeParticipantRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChallengeService 단위 테스트 — 개설권 조회 + 챌린지 개설.
 */
@ExtendWith(MockitoExtension.class)
class ChallengeServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    ChallengeDexRepository challengeDexRepository;
    @Mock
    ChallengeDexSlotRepository slotRepository;
    @Mock
    ChallengeParticipantRepository participantRepository;

    @InjectMocks
    ChallengeService challengeService;

    private ChallengeDex sampleDex() {
        return ChallengeDex.builder()
                .ownerId(99L).name("샘플 챌린지").description("설명")
                .challengeType(ChallengeType.COLLECTION).periodType(PeriodType.PERMANENT)
                .startsAt(LocalDateTime.now().minusDays(1)).event(false)
                .build();
    }

    private User newUser() {
        return User.builder()
                .provider(Provider.GOOGLE).providerId("p")
                .nickname("n").email("e@e").role(Role.USER)
                .build();
    }

    private int currentYearMonth() {
        YearMonth m = YearMonth.now();
        return m.getYear() * 100 + m.getMonthValue();
    }

    /** 슬롯 N개짜리 개설 요청. */
    private ChallengeCreateRequestDTO req(PeriodType period, LocalDateTime endsAt, int slotCount) {
        List<SlotInput> slots = new ArrayList<>();
        for (int i = 0; i < slotCount; i++) {
            slots.add(new SlotInput("음식" + i, null, null, null, null));
        }
        return new ChallengeCreateRequestDTO(
                "테스트 챌린지", "설명",
                ChallengeType.COLLECTION, period,
                null, endsAt, null, slots);
    }

    @Test
    @DisplayName("개설권 조회 시 이번 달 3개로 리필된다")
    void getRemainingTickets_refillsToThree() {
        User user = newUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        CreationTicketResponseDTO res = challengeService.getRemainingTickets(1L);

        assertThat(res.remaining()).isEqualTo(3);
    }

    @Test
    @DisplayName("정상 개설 시 개설권 1장이 소진되고 슬롯이 저장된다")
    void create_success() {
        User user = newUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(challengeDexRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ChallengeCreateResponseDTO res =
                challengeService.create(1L, req(PeriodType.PERMANENT, null, 5));

        assertThat(res.remainingTickets()).isEqualTo(2);   // 3 → 2
        verify(challengeDexRepository).save(any());
        verify(slotRepository).saveAll(any());
    }

    @Test
    @DisplayName("슬롯이 5개 미만이면 CHALLENGE_SLOT_MIN_REQUIRED, 저장하지 않는다")
    void create_tooFewSlots() {
        assertThatThrownBy(() -> challengeService.create(1L, req(PeriodType.PERMANENT, null, 4)))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CHALLENGE_SLOT_MIN_REQUIRED));
        verify(challengeDexRepository, never()).save(any());
    }

    @Test
    @DisplayName("기간 한정인데 종료 시각이 없으면 CHALLENGE_PERIOD_INVALID")
    void create_limitedWithoutEnds() {
        assertThatThrownBy(() -> challengeService.create(1L, req(PeriodType.LIMITED, null, 5)))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CHALLENGE_PERIOD_INVALID));
    }

    @Test
    @DisplayName("이번 달 개설권을 다 쓰면 CHALLENGE_TICKET_EXHAUSTED")
    void create_noTicketsLeft() {
        User user = newUser();
        int ym = currentYearMonth();
        user.useChallengeTicket(ym);   // 3 → 2
        user.useChallengeTicket(ym);   // 2 → 1
        user.useChallengeTicket(ym);   // 1 → 0
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> challengeService.create(1L, req(PeriodType.PERMANENT, null, 5)))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CHALLENGE_TICKET_EXHAUSTED));
    }

    @Test
    @DisplayName("진행중 탐색은 진행중 목록을 요약 DTO로 돌려준다")
    void getChallenges_ongoing() {
        when(challengeDexRepository.findOngoing(any())).thenReturn(List.of(sampleDex()));
        when(participantRepository.countByChallengeDexId(any())).thenReturn(3L);

        List<ChallengeSummaryDTO> result = challengeService.getChallenges(ChallengeListStatus.ONGOING);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("샘플 챌린지");
        assertThat(result.get(0).participantCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("완료 탐색은 finished 쿼리를 사용한다")
    void getChallenges_finished() {
        when(challengeDexRepository.findFinished(any())).thenReturn(List.of(sampleDex()));
        when(participantRepository.countByChallengeDexId(any())).thenReturn(0L);

        List<ChallengeSummaryDTO> result = challengeService.getChallenges(ChallengeListStatus.FINISHED);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).participantCount()).isEqualTo(0);
    }
}
