package com.backend_catcheat.domain.made.service;

import com.backend_catcheat.domain.auth.entity.Provider;
import com.backend_catcheat.domain.auth.entity.Role;
import com.backend_catcheat.domain.auth.entity.User;
import com.backend_catcheat.domain.auth.repository.UserRepository;
import com.backend_catcheat.domain.made.dto.MadeDexMemberDTO;
import com.backend_catcheat.domain.made.dto.MadeDexMembersResponseDTO;
import com.backend_catcheat.domain.made.entity.MadeDex;
import com.backend_catcheat.domain.made.entity.MadeDexMember;
import com.backend_catcheat.domain.made.entity.MadeDexRole;
import com.backend_catcheat.domain.made.entity.Visibility;
import com.backend_catcheat.domain.made.repository.MadeDexInviteRepository;
import com.backend_catcheat.domain.made.repository.MadeDexMemberRepository;
import com.backend_catcheat.domain.made.repository.MadeDexRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import com.backend_catcheat.global.s3.S3PresignedUrlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 그룹 삭제 시각을 sleep 없이 검증하려고 고정 시계를 주입한다
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MadeDexMemberServiceTest {

    private static final long OWNER_ID = 1L;
    private static final long MEMBER_ID = 2L;
    private static final long STRANGER_ID = 3L;
    private static final long MADE_DEX_ID = 10L;

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 4, 10, 0);

    private final Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    @Mock
    MadeDexRepository madeDexRepository;
    @Mock
    MadeDexMemberRepository madeDexMemberRepository;
    @Mock
    MadeDexInviteRepository madeDexInviteRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    S3PresignedUrlService s3PresignedUrlService;

    // 조회 헬퍼는 진짜를 쓴다. 목으로 감싸면 "삭제된 그룹" 케이스가 스텁 설정만 검증하게 된다
    private MadeDexMemberService service() {
        return new MadeDexMemberService(
                new MadeDexFinder(madeDexRepository, madeDexMemberRepository), madeDexMemberRepository,
                madeDexInviteRepository, userRepository, s3PresignedUrlService, clock);
    }

    private MadeDex madeDex(Long ownerId) {
        MadeDex madeDex = MadeDex.open(ownerId, "우리 도감", "설명", Visibility.PRIVATE, null);
        ReflectionTestUtils.setField(madeDex, "id", MADE_DEX_ID);
        return madeDex;
    }

    private User user(Long id, String nickname) {
        User user = User.builder()
                .provider(Provider.KAKAO).providerId(String.valueOf(id))
                .nickname(nickname).email(null).role(Role.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private MadeDexMember owner(Long userId, LocalDateTime joinedAt) {
        return MadeDexMember.owner(MADE_DEX_ID, userId, joinedAt);
    }

    private MadeDexMember member(Long userId, LocalDateTime joinedAt) {
        return MadeDexMember.member(MADE_DEX_ID, userId, joinedAt);
    }

    private void givenOwnerAndMember() {
        when(madeDexRepository.findByIdAndDeletedAtIsNull(MADE_DEX_ID))
                .thenReturn(Optional.of(madeDex(OWNER_ID)));
        when(madeDexRepository.findActiveByIdForUpdate(MADE_DEX_ID))
                .thenReturn(Optional.of(madeDex(OWNER_ID)));
        when(madeDexMemberRepository.findByMadeDexIdAndUserId(MADE_DEX_ID, OWNER_ID))
                .thenReturn(Optional.of(owner(OWNER_ID, NOW.minusDays(3))));
        when(madeDexMemberRepository.findByMadeDexIdAndUserId(MADE_DEX_ID, MEMBER_ID))
                .thenReturn(Optional.of(member(MEMBER_ID, NOW.minusDays(1))));
        when(madeDexMemberRepository.findByMadeDexIdAndUserId(MADE_DEX_ID, STRANGER_ID))
                .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("그룹장이 먼저 오고 나머지는 들어온 순서대로 온다")
    void findMembers_ownerFirst() {
        givenOwnerAndMember();
        // 저장 순서를 뒤집어 둬도 그룹장이 위로 올라오는지 본다
        when(madeDexMemberRepository.findByMadeDexIdOrderByJoinedAtAscIdAsc(MADE_DEX_ID))
                .thenReturn(List.of(
                        member(MEMBER_ID, NOW.minusDays(2)),
                        member(STRANGER_ID, NOW.minusDays(1)),
                        owner(OWNER_ID, NOW.minusDays(3))));
        when(userRepository.findAllById(any()))
                .thenReturn(List.of(user(OWNER_ID, "방장"), user(MEMBER_ID, "친구"), user(STRANGER_ID, "다른친구")));

        MadeDexMembersResponseDTO response = service().findMembers(OWNER_ID, MADE_DEX_ID);

        assertThat(response.members()).extracting(MadeDexMemberDTO::userId)
                .containsExactly(OWNER_ID, MEMBER_ID, STRANGER_ID);
        assertThat(response.members().get(0).role()).isEqualTo(MadeDexRole.OWNER);
        assertThat(response.maxMembers()).isEqualTo(MadeDex.MAX_MEMBERS);
        assertThat(response.myRole()).isEqualTo(MadeDexRole.OWNER);
    }

    @Test
    @DisplayName("내 행에는 me 표시가 붙는다")
    void findMembers_marksMe() {
        givenOwnerAndMember();
        when(madeDexMemberRepository.findByMadeDexIdOrderByJoinedAtAscIdAsc(MADE_DEX_ID))
                .thenReturn(List.of(owner(OWNER_ID, NOW.minusDays(3)), member(MEMBER_ID, NOW.minusDays(1))));
        when(userRepository.findAllById(any()))
                .thenReturn(List.of(user(OWNER_ID, "방장"), user(MEMBER_ID, "친구")));

        MadeDexMembersResponseDTO response = service().findMembers(MEMBER_ID, MADE_DEX_ID);

        assertThat(response.members()).filteredOn(MadeDexMemberDTO::me)
                .extracting(MadeDexMemberDTO::userId).containsExactly(MEMBER_ID);
        assertThat(response.myRole()).isEqualTo(MadeDexRole.MEMBER);
    }

    @Test
    @DisplayName("비공개 그룹이라 멤버가 아니면 목록을 볼 수 없다")
    void findMembers_notMember() {
        givenOwnerAndMember();

        assertThatThrownBy(() -> service().findMembers(STRANGER_ID, MADE_DEX_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MADE_DEX_NOT_MEMBER));
    }

    @Test
    @DisplayName("그룹장은 참여자를 내보낼 수 있다")
    void kick_removesMember() {
        givenOwnerAndMember();
        when(madeDexMemberRepository.deleteByMadeDexIdAndUserId(MADE_DEX_ID, MEMBER_ID)).thenReturn(1L);

        service().kick(OWNER_ID, MADE_DEX_ID, MEMBER_ID);

        verify(madeDexMemberRepository).deleteByMadeDexIdAndUserId(MADE_DEX_ID, MEMBER_ID);
    }

    @Test
    @DisplayName("일반 멤버는 다른 사람을 내보낼 수 없다")
    void kick_notOwner() {
        givenOwnerAndMember();

        assertThatThrownBy(() -> service().kick(MEMBER_ID, MADE_DEX_ID, STRANGER_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MADE_DEX_NOT_OWNER));

        verify(madeDexMemberRepository, never()).deleteByMadeDexIdAndUserId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("그룹장이 스스로를 내보내려 하면 막는다 — 주인 없는 방이 되기 때문")
    void kick_self() {
        givenOwnerAndMember();

        assertThatThrownBy(() -> service().kick(OWNER_ID, MADE_DEX_ID, OWNER_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MADE_DEX_CANNOT_KICK_SELF));

        verify(madeDexMemberRepository, never()).deleteByMadeDexIdAndUserId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("이미 나간 사람을 내보내려 하면 알려준다")
    void kick_alreadyGone() {
        givenOwnerAndMember();
        when(madeDexMemberRepository.deleteByMadeDexIdAndUserId(MADE_DEX_ID, STRANGER_ID)).thenReturn(0L);

        assertThatThrownBy(() -> service().kick(OWNER_ID, MADE_DEX_ID, STRANGER_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MADE_DEX_MEMBER_NOT_FOUND));
    }

    @Test
    @DisplayName("일반 멤버가 나가면 자기 행만 지워지고 그룹은 남는다")
    void leave_member() {
        givenOwnerAndMember();
        MadeDex madeDex = madeDex(OWNER_ID);
        when(madeDexRepository.findActiveByIdForUpdate(MADE_DEX_ID)).thenReturn(Optional.of(madeDex));

        assertThat(service().leave(MEMBER_ID, MADE_DEX_ID).groupDeleted()).isFalse();

        verify(madeDexMemberRepository).deleteByMadeDexIdAndUserId(MADE_DEX_ID, MEMBER_ID);
        assertThat(madeDex.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("그룹장이 나가면 방이 사라지고 남은 코드도 죽는다")
    void leave_owner_deletesGroup() {
        givenOwnerAndMember();
        MadeDex madeDex = madeDex(OWNER_ID);
        when(madeDexRepository.findActiveByIdForUpdate(MADE_DEX_ID)).thenReturn(Optional.of(madeDex));

        assertThat(service().leave(OWNER_ID, MADE_DEX_ID).groupDeleted()).isTrue();

        assertThat(madeDex.getDeletedAt()).isEqualTo(NOW);
        verify(madeDexInviteRepository).revokeActive(MADE_DEX_ID, NOW);
        // 방이 통째로 사라지므로 멤버 행을 따로 지우지 않는다
        verify(madeDexMemberRepository, never()).deleteByMadeDexIdAndUserId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("참여하지 않은 그룹에서는 나갈 수 없다")
    void leave_notMember() {
        givenOwnerAndMember();

        assertThatThrownBy(() -> service().leave(STRANGER_ID, MADE_DEX_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MADE_DEX_NOT_MEMBER));
    }

    @Test
    @DisplayName("위임하면 owner_id와 두 사람의 role이 함께 바뀐다")
    void transferOwner_movesBothPlaces() {
        MadeDex madeDex = madeDex(OWNER_ID);
        MadeDexMember current = owner(OWNER_ID, NOW.minusDays(3));
        MadeDexMember next = member(MEMBER_ID, NOW.minusDays(1));
        when(madeDexRepository.findActiveByIdForUpdate(MADE_DEX_ID)).thenReturn(Optional.of(madeDex));
        when(madeDexMemberRepository.findByMadeDexIdAndUserId(MADE_DEX_ID, OWNER_ID))
                .thenReturn(Optional.of(current));
        when(madeDexMemberRepository.findByMadeDexIdAndUserId(MADE_DEX_ID, MEMBER_ID))
                .thenReturn(Optional.of(next));

        service().transferOwner(OWNER_ID, MADE_DEX_ID, MEMBER_ID);

        assertThat(madeDex.getOwnerId()).isEqualTo(MEMBER_ID);
        assertThat(next.getRole()).isEqualTo(MadeDexRole.OWNER);
        assertThat(current.getRole()).isEqualTo(MadeDexRole.MEMBER);
    }

    @Test
    @DisplayName("그룹장이 아니면 위임할 수 없다")
    void transferOwner_notOwner() {
        givenOwnerAndMember();

        assertThatThrownBy(() -> service().transferOwner(MEMBER_ID, MADE_DEX_ID, STRANGER_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MADE_DEX_NOT_OWNER));
    }

    @Test
    @DisplayName("자기 자신에게는 위임할 수 없다")
    void transferOwner_self() {
        givenOwnerAndMember();

        assertThatThrownBy(() -> service().transferOwner(OWNER_ID, MADE_DEX_ID, OWNER_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MADE_DEX_ALREADY_OWNER));
    }

    @Test
    @DisplayName("참여자가 아닌 사람에게는 위임할 수 없다")
    void transferOwner_targetNotMember() {
        givenOwnerAndMember();

        assertThatThrownBy(() -> service().transferOwner(OWNER_ID, MADE_DEX_ID, STRANGER_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MADE_DEX_MEMBER_NOT_FOUND));
    }
}
