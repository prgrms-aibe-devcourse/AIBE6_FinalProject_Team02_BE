package com.backend_catcheat.domain.made.service;

import com.backend_catcheat.domain.made.dto.MadeDexInvitePreviewDTO;
import com.backend_catcheat.domain.made.dto.MadeDexInviteResponseDTO;
import com.backend_catcheat.domain.made.entity.MadeDex;
import com.backend_catcheat.domain.made.entity.MadeDexInvite;
import com.backend_catcheat.domain.made.entity.MadeDexMember;
import com.backend_catcheat.domain.made.entity.MadeDexRole;
import com.backend_catcheat.domain.made.repository.MadeDexInviteRepository;
import com.backend_catcheat.domain.made.repository.MadeDexMemberRepository;
import com.backend_catcheat.domain.made.repository.MadeDexRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 초대 코드 발급 / 코드로 참여 단위 테스트.
 * 만료 판정을 sleep 없이 검증하려고 고정 시계를 주입한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MadeDexInviteServiceTest {

    private static final long OWNER_ID = 1L;
    private static final long JOINER_ID = 2L;
    private static final long MADE_DEX_ID = 10L;
    private static final String CODE = "ABC123";

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 10, 0);

    private final Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    @Mock
    MadeDexRepository madeDexRepository;
    @Mock
    MadeDexMemberRepository madeDexMemberRepository;
    @Mock
    MadeDexInviteRepository madeDexInviteRepository;
    @Mock
    InviteCodeGenerator inviteCodeGenerator;

    private MadeDexInviteService service() {
        return new MadeDexInviteService(
                new MadeDexFinder(madeDexRepository, madeDexMemberRepository), madeDexMemberRepository,
                madeDexInviteRepository, inviteCodeGenerator, clock);
    }

    private MadeDex madeDex(Long ownerId) {
        MadeDex madeDex = MadeDex.open(ownerId, "우리 도감", "설명", null);
        ReflectionTestUtils.setField(madeDex, "id", MADE_DEX_ID);
        return madeDex;
    }

    private MadeDexInvite invite(LocalDateTime issuedAt) {
        return MadeDexInvite.issue(MADE_DEX_ID, CODE, OWNER_ID, issuedAt);
    }

    // ---------- 발급 ----------

    @Test
    @DisplayName("발급하면 7일 뒤 만료로 저장된다")
    void issue_expiresInSevenDays() {
        when(madeDexRepository.findActiveByIdForUpdate(MADE_DEX_ID))
                .thenReturn(Optional.of(madeDex(OWNER_ID)));
        when(inviteCodeGenerator.generate()).thenReturn(CODE);
        when(madeDexInviteRepository.existsByCode(CODE)).thenReturn(false);
        when(madeDexInviteRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MadeDexInviteResponseDTO response = service().issue(OWNER_ID, MADE_DEX_ID);

        assertThat(response.code()).isEqualTo(CODE);
        assertThat(response.expiresAt()).isEqualTo(NOW.plusDays(MadeDexInvite.TTL_DAYS));
    }

    @Test
    @DisplayName("재발급하면 살아 있던 코드를 먼저 무효화한다")
    void issue_revokesPrevious() {
        when(madeDexRepository.findActiveByIdForUpdate(MADE_DEX_ID))
                .thenReturn(Optional.of(madeDex(OWNER_ID)));
        when(inviteCodeGenerator.generate()).thenReturn("XYZ789");
        when(madeDexInviteRepository.existsByCode(anyString())).thenReturn(false);
        when(madeDexInviteRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service().issue(OWNER_ID, MADE_DEX_ID);

        verify(madeDexInviteRepository).revokeActive(MADE_DEX_ID, NOW);
    }

    @Test
    @DisplayName("코드가 이미 쓰이고 있으면 다시 뽑는다")
    void issue_retriesOnCollision() {
        when(madeDexRepository.findActiveByIdForUpdate(MADE_DEX_ID))
                .thenReturn(Optional.of(madeDex(OWNER_ID)));
        when(inviteCodeGenerator.generate()).thenReturn(CODE, "XYZ789");
        when(madeDexInviteRepository.existsByCode(CODE)).thenReturn(true);
        when(madeDexInviteRepository.existsByCode("XYZ789")).thenReturn(false);
        when(madeDexInviteRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(service().issue(OWNER_ID, MADE_DEX_ID).code()).isEqualTo("XYZ789");
    }

    @Test
    @DisplayName("그룹장이 아니면 발급할 수 없다")
    void issue_notOwner() {
        when(madeDexRepository.findActiveByIdForUpdate(MADE_DEX_ID))
                .thenReturn(Optional.of(madeDex(OWNER_ID)));

        assertThatThrownBy(() -> service().issue(JOINER_ID, MADE_DEX_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MADE_DEX_NOT_OWNER));

        verify(madeDexInviteRepository, never()).save(any());
        verify(madeDexInviteRepository, never()).revokeActive(anyLong(), any());
    }

    @Test
    @DisplayName("삭제된 그룹은 발급할 수 없다")
    void issue_deletedGroup() {
        when(madeDexRepository.findActiveByIdForUpdate(MADE_DEX_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().issue(OWNER_ID, MADE_DEX_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MADE_DEX_NOT_FOUND));
    }

    @Test
    @DisplayName("발급도 참여와 같은 그룹 행을 잠근다")
    void issue_locksGroupRow() {
        when(madeDexRepository.findActiveByIdForUpdate(MADE_DEX_ID))
                .thenReturn(Optional.of(madeDex(OWNER_ID)));
        when(inviteCodeGenerator.generate()).thenReturn(CODE);
        when(madeDexInviteRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service().issue(OWNER_ID, MADE_DEX_ID);

        // 잠그지 않으면 참여 중인 요청이 방금 죽인 코드로 들어올 수 있다
        verify(madeDexRepository).findActiveByIdForUpdate(MADE_DEX_ID);
    }

    @Test
    @DisplayName("유효한 코드가 없으면 조회 결과는 null이다")
    void findActive_none() {
        when(madeDexRepository.findByIdAndDeletedAtIsNull(MADE_DEX_ID))
                .thenReturn(Optional.of(madeDex(OWNER_ID)));
        when(madeDexInviteRepository
                .findFirstByMadeDexIdAndRevokedAtIsNullAndExpiresAtAfterOrderByIdDesc(MADE_DEX_ID, NOW))
                .thenReturn(Optional.empty());

        assertThat(service().findActive(OWNER_ID, MADE_DEX_ID)).isNull();
    }

    // ---------- 참여 ----------

    @Test
    @DisplayName("코드로 참여하면 MEMBER 역할로 저장된다")
    void join_savesMember() {
        givenUsableInvite();
        when(madeDexRepository.findActiveByIdForUpdate(MADE_DEX_ID))
                .thenReturn(Optional.of(madeDex(OWNER_ID)));
        when(madeDexMemberRepository.existsByMadeDexIdAndUserId(MADE_DEX_ID, JOINER_ID)).thenReturn(false);
        when(madeDexMemberRepository.countByMadeDexId(MADE_DEX_ID)).thenReturn(3L);

        Long madeDexId = service().join(JOINER_ID, CODE).madeDexId();

        ArgumentCaptor<MadeDexMember> captor = ArgumentCaptor.forClass(MadeDexMember.class);
        verify(madeDexMemberRepository).save(captor.capture());
        MadeDexMember member = captor.getValue();

        assertThat(madeDexId).isEqualTo(MADE_DEX_ID);
        assertThat(member.getUserId()).isEqualTo(JOINER_ID);
        assertThat(member.getRole()).isEqualTo(MadeDexRole.MEMBER);
        assertThat(member.getJoinedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("소문자·공백이 섞여 들어와도 같은 코드로 본다")
    void join_normalizesCode() {
        givenUsableInvite();
        when(madeDexRepository.findActiveByIdForUpdate(MADE_DEX_ID))
                .thenReturn(Optional.of(madeDex(OWNER_ID)));
        when(madeDexMemberRepository.countByMadeDexId(MADE_DEX_ID)).thenReturn(1L);

        assertThat(service().join(JOINER_ID, " abc-123 ").madeDexId()).isEqualTo(MADE_DEX_ID);
    }

    @Test
    @DisplayName("코드가 비어 있으면 입력을 요구한다")
    void join_blankCode() {
        assertThatThrownBy(() -> service().join(JOINER_ID, "  "))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MADE_DEX_INVITE_CODE_REQUIRED));

        verify(madeDexInviteRepository, never()).findByCode(anyString());
    }

    @Test
    @DisplayName("없는 코드면 INVALID")
    void join_unknownCode() {
        when(madeDexInviteRepository.findByCode(CODE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().join(JOINER_ID, CODE))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MADE_DEX_INVITE_CODE_INVALID));
    }

    @Test
    @DisplayName("발급 후 7일이 지난 코드는 EXPIRED")
    void join_expiredCode() {
        when(madeDexInviteRepository.findByCode(CODE))
                .thenReturn(Optional.of(invite(NOW.minusDays(MadeDexInvite.TTL_DAYS))));

        assertThatThrownBy(() -> service().join(JOINER_ID, CODE))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MADE_DEX_INVITE_CODE_EXPIRED));

        verify(madeDexMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("재발급으로 무효화된 코드도 EXPIRED로 돌려준다")
    void join_revokedCode() {
        MadeDexInvite revoked = invite(NOW.minusDays(1));
        ReflectionTestUtils.setField(revoked, "revokedAt", NOW.minusHours(1));
        when(madeDexInviteRepository.findByCode(CODE)).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service().join(JOINER_ID, CODE))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MADE_DEX_INVITE_CODE_EXPIRED));
    }

    @Test
    @DisplayName("이미 참여 중이면 ALREADY_JOINED")
    void join_alreadyMember() {
        givenUsableInvite();
        when(madeDexRepository.findActiveByIdForUpdate(MADE_DEX_ID))
                .thenReturn(Optional.of(madeDex(OWNER_ID)));
        when(madeDexMemberRepository.existsByMadeDexIdAndUserId(MADE_DEX_ID, JOINER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service().join(JOINER_ID, CODE))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MADE_DEX_ALREADY_JOINED));

        verify(madeDexMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("정원 12명이 차면 FULL, 저장하지 않는다")
    void join_full() {
        givenUsableInvite();
        when(madeDexRepository.findActiveByIdForUpdate(MADE_DEX_ID))
                .thenReturn(Optional.of(madeDex(OWNER_ID)));
        when(madeDexMemberRepository.countByMadeDexId(MADE_DEX_ID))
                .thenReturn((long) MadeDex.MAX_MEMBERS);

        assertThatThrownBy(() -> service().join(JOINER_ID, CODE))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MADE_DEX_FULL));

        verify(madeDexMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("잠금을 기다리는 사이 재발급되면 EXPIRED로 막힌다")
    void join_revokedWhileWaitingForLock() {
        givenUsableInvite();
        when(madeDexRepository.findActiveByIdForUpdate(MADE_DEX_ID))
                .thenReturn(Optional.of(madeDex(OWNER_ID)));
        // 잠금 전 검증은 통과했지만, 잠금을 얻고 다시 보니 죽어 있다
        when(madeDexInviteRepository.existsUsableByCode(CODE, NOW)).thenReturn(false);

        assertThatThrownBy(() -> service().join(JOINER_ID, CODE))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MADE_DEX_INVITE_CODE_EXPIRED));

        verify(madeDexMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("참여는 그룹 행을 잠그고 정원을 센다")
    void join_locksGroupRow() {
        givenUsableInvite();
        when(madeDexRepository.findActiveByIdForUpdate(MADE_DEX_ID))
                .thenReturn(Optional.of(madeDex(OWNER_ID)));
        when(madeDexMemberRepository.countByMadeDexId(MADE_DEX_ID)).thenReturn(1L);

        service().join(JOINER_ID, CODE);

        verify(madeDexRepository).findActiveByIdForUpdate(MADE_DEX_ID);
    }

    // ---------- 미리보기 ----------

    @Test
    @DisplayName("미리보기는 그룹 정보와 내 참여 여부를 함께 준다")
    void preview_showsGroupAndMembership() {
        givenUsableInvite();
        when(madeDexMemberRepository.countByMadeDexId(MADE_DEX_ID)).thenReturn(4L);
        when(madeDexMemberRepository.existsByMadeDexIdAndUserId(MADE_DEX_ID, JOINER_ID)).thenReturn(true);

        MadeDexInvitePreviewDTO preview = service().preview(JOINER_ID, CODE);

        assertThat(preview.madeDexId()).isEqualTo(MADE_DEX_ID);
        assertThat(preview.name()).isEqualTo("우리 도감");
        assertThat(preview.memberCount()).isEqualTo(4L);
        assertThat(preview.maxMembers()).isEqualTo(MadeDex.MAX_MEMBERS);
        assertThat(preview.alreadyMember()).isTrue();
    }

    @Test
    @DisplayName("코드가 가리키는 그룹이 지워졌으면 NOT_FOUND")
    void preview_deletedGroup() {
        when(madeDexInviteRepository.findByCode(CODE)).thenReturn(Optional.of(invite(NOW)));
        when(madeDexRepository.findByIdAndDeletedAtIsNull(MADE_DEX_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().preview(JOINER_ID, CODE))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MADE_DEX_NOT_FOUND));
    }

    /** 오늘 발급된, 아직 살아 있는 코드. 잠금 뒤 재확인도 통과한다 */
    private void givenUsableInvite() {
        when(madeDexInviteRepository.findByCode(eq(CODE))).thenReturn(Optional.of(invite(NOW)));
        when(madeDexInviteRepository.existsUsableByCode(CODE, NOW)).thenReturn(true);
        when(madeDexRepository.findByIdAndDeletedAtIsNull(MADE_DEX_ID))
                .thenReturn(Optional.of(madeDex(OWNER_ID)));
    }
}
