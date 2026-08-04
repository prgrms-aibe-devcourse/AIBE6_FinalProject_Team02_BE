package com.backend_catcheat.domain.made.service;

import com.backend_catcheat.domain.made.dto.MadeDexCreateRequestDTO;
import com.backend_catcheat.domain.made.dto.MadeDexMemberCountDTO;
import com.backend_catcheat.domain.made.dto.MadeDexSummaryDTO;
import com.backend_catcheat.domain.made.entity.MadeDex;
import com.backend_catcheat.domain.made.entity.MadeDexMember;
import com.backend_catcheat.domain.made.entity.MadeDexRole;
import com.backend_catcheat.domain.made.entity.Visibility;
import com.backend_catcheat.domain.made.repository.MadeDexMemberRepository;
import com.backend_catcheat.domain.made.repository.MadeDexRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MadeDexService 단위 테스트 — 그룹 개설 + 내 그룹 목록.
 */
@ExtendWith(MockitoExtension.class)
class MadeDexServiceTest {

    private static final long OWNER_ID = 1L;

    @Mock
    MadeDexRepository madeDexRepository;
    @Mock
    MadeDexMemberRepository madeDexMemberRepository;

    @InjectMocks
    MadeDexService madeDexService;

    private MadeDex savedMadeDex(Long id, String name, Visibility visibility) {
        MadeDex madeDex = MadeDex.open(OWNER_ID, name, null, visibility);
        ReflectionTestUtils.setField(madeDex, "id", id);
        return madeDex;
    }

    private MadeDexMember membership(Long madeDexId, MadeDexRole role) {
        LocalDateTime joinedAt = LocalDateTime.now();
        return role == MadeDexRole.OWNER
                ? MadeDexMember.owner(madeDexId, OWNER_ID, joinedAt)
                : MadeDexMember.member(madeDexId, OWNER_ID, joinedAt);
    }

    @Test
    @DisplayName("개설하면 개설자가 OWNER 멤버로 함께 저장된다")
    void create_savesOwnerAsMember() {
        when(madeDexRepository.save(any())).thenReturn(savedMadeDex(10L, "우리 도감", Visibility.PRIVATE));

        Long madeDexId = madeDexService.create(
                OWNER_ID, new MadeDexCreateRequestDTO("우리 도감", null, Visibility.PRIVATE)).madeDexId();

        ArgumentCaptor<MadeDexMember> captor = ArgumentCaptor.forClass(MadeDexMember.class);
        verify(madeDexMemberRepository).save(captor.capture());
        MadeDexMember member = captor.getValue();

        assertThat(madeDexId).isEqualTo(10L);
        assertThat(member.getMadeDexId()).isEqualTo(10L);
        assertThat(member.getUserId()).isEqualTo(OWNER_ID);
        assertThat(member.isOwner()).isTrue();
    }

    @Test
    @DisplayName("공개 설정을 안 주면 비공개로 개설된다")
    void create_defaultsToPrivate() {
        when(madeDexRepository.save(any())).thenAnswer(invocation -> {
            MadeDex madeDex = invocation.getArgument(0);
            ReflectionTestUtils.setField(madeDex, "id", 10L);
            return madeDex;
        });

        madeDexService.create(OWNER_ID, new MadeDexCreateRequestDTO("우리 도감", null, null));

        ArgumentCaptor<MadeDex> captor = ArgumentCaptor.forClass(MadeDex.class);
        verify(madeDexRepository).save(captor.capture());
        assertThat(captor.getValue().getVisibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(captor.getValue().getMaxMembers()).isEqualTo(MadeDex.MAX_MEMBERS);
    }

    @Test
    @DisplayName("이름이 공백뿐이면 MADE_DEX_NAME_REQUIRED, 저장하지 않는다")
    void create_blankName() {
        assertThatThrownBy(() -> madeDexService.create(
                OWNER_ID, new MadeDexCreateRequestDTO("   ", null, Visibility.PRIVATE)))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MADE_DEX_NAME_REQUIRED));

        verify(madeDexRepository, never()).save(any());
        verify(madeDexMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("이름이 100자를 넘으면 MADE_DEX_NAME_TOO_LONG")
    void create_nameTooLong() {
        String name = "가".repeat(MadeDex.NAME_MAX + 1);

        assertThatThrownBy(() -> madeDexService.create(
                OWNER_ID, new MadeDexCreateRequestDTO(name, null, Visibility.PRIVATE)))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MADE_DEX_NAME_TOO_LONG));

        verify(madeDexRepository, never()).save(any());
    }

    @Test
    @DisplayName("소개가 500자를 넘으면 MADE_DEX_DESCRIPTION_TOO_LONG")
    void create_descriptionTooLong() {
        String description = "가".repeat(MadeDex.DESCRIPTION_MAX + 1);

        assertThatThrownBy(() -> madeDexService.create(
                OWNER_ID, new MadeDexCreateRequestDTO("우리 도감", description, Visibility.PRIVATE)))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MADE_DEX_DESCRIPTION_TOO_LONG));

        verify(madeDexRepository, never()).save(any());
    }

    @Test
    @DisplayName("목록은 멤버 수와 내 역할을 함께 준다")
    void findMine_fillsMemberCountAndRole() {
        when(madeDexMemberRepository.findByUserId(OWNER_ID))
                .thenReturn(List.of(membership(10L, MadeDexRole.OWNER), membership(11L, MadeDexRole.MEMBER)));
        when(madeDexMemberRepository.countByMadeDexIds(anyCollection()))
                .thenReturn(List.of(new MadeDexMemberCountDTO(10L, 3L), new MadeDexMemberCountDTO(11L, 12L)));
        when(madeDexRepository.findByIdInAndDeletedAtIsNullOrderByCreatedAtDesc(anyCollection()))
                .thenReturn(List.of(
                        savedMadeDex(10L, "우리 도감", Visibility.PRIVATE),
                        savedMadeDex(11L, "회사 점심 도감", Visibility.PUBLIC)));

        List<MadeDexSummaryDTO> summaries = madeDexService.findMine(OWNER_ID);

        assertThat(summaries).extracting(
                        MadeDexSummaryDTO::id, MadeDexSummaryDTO::memberCount, MadeDexSummaryDTO::myRole)
                .containsExactly(
                        tuple(10L, 3L, MadeDexRole.OWNER),
                        tuple(11L, 12L, MadeDexRole.MEMBER));
    }

    @Test
    @DisplayName("삭제된 그룹은 목록에서 빠진다")
    void findMine_excludesDeleted() {
        when(madeDexMemberRepository.findByUserId(OWNER_ID))
                .thenReturn(List.of(membership(10L, MadeDexRole.OWNER)));
        when(madeDexMemberRepository.countByMadeDexIds(anyCollection()))
                .thenReturn(List.of(new MadeDexMemberCountDTO(10L, 1L)));
        when(madeDexRepository.findByIdInAndDeletedAtIsNullOrderByCreatedAtDesc(anyCollection()))
                .thenReturn(List.of());

        assertThat(madeDexService.findMine(OWNER_ID)).isEmpty();
    }

    @Test
    @DisplayName("가입한 그룹이 없으면 조회를 더 하지 않는다")
    void findMine_noMembership() {
        when(madeDexMemberRepository.findByUserId(OWNER_ID)).thenReturn(List.of());

        assertThat(madeDexService.findMine(OWNER_ID)).isEmpty();
        verify(madeDexRepository, never()).findByIdInAndDeletedAtIsNullOrderByCreatedAtDesc(anyCollection());
    }
}
