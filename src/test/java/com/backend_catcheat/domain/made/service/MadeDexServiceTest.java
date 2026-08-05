package com.backend_catcheat.domain.made.service;

import com.backend_catcheat.domain.made.dto.MadeDexCreateRequestDTO;
import com.backend_catcheat.domain.made.dto.MadeDexDetailDTO;
import com.backend_catcheat.domain.made.dto.MadeDexMemberCountDTO;
import com.backend_catcheat.domain.made.dto.MadeDexSummaryDTO;
import com.backend_catcheat.domain.made.dto.MadeDexUpdateRequestDTO;
import com.backend_catcheat.domain.made.entity.MadeDex;
import com.backend_catcheat.domain.made.entity.MadeDexMember;
import com.backend_catcheat.domain.made.entity.MadeDexRole;
import com.backend_catcheat.domain.made.entity.Visibility;
import com.backend_catcheat.domain.made.repository.MadeDexMemberRepository;
import com.backend_catcheat.domain.made.repository.MadeDexRepository;
import com.backend_catcheat.global.event.S3ObjectUnusedEvent;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import com.backend_catcheat.global.s3.S3PresignedUrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

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
    private static final long STRANGER_ID = 9L;

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 4, 10, 0);

    @Mock
    MadeDexRepository madeDexRepository;
    @Mock
    MadeDexMemberRepository madeDexMemberRepository;
    @Mock
    S3PresignedUrlService s3PresignedUrlService;
    @Mock
    ApplicationEventPublisher eventPublisher;

    MadeDexService madeDexService;

    // @InjectMocks는 Clock까지 목으로 넣어 now()가 null이 된다
    @BeforeEach
    void setUp() {
        madeDexService = new MadeDexService(
                madeDexRepository, madeDexMemberRepository, new MadeDexFinder(madeDexRepository),
                s3PresignedUrlService, eventPublisher,
                Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
    }

    private MadeDex savedMadeDex(Long id, String name, Visibility visibility) {
        MadeDex madeDex = MadeDex.open(OWNER_ID, name, null, visibility, null);
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
                OWNER_ID, new MadeDexCreateRequestDTO("우리 도감", null, Visibility.PRIVATE, null)).madeDexId();

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

        madeDexService.create(OWNER_ID, new MadeDexCreateRequestDTO("우리 도감", null, null, null));

        ArgumentCaptor<MadeDex> captor = ArgumentCaptor.forClass(MadeDex.class);
        verify(madeDexRepository).save(captor.capture());
        assertThat(captor.getValue().getVisibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(captor.getValue().getMaxMembers()).isEqualTo(MadeDex.MAX_MEMBERS);
    }

    @Test
    @DisplayName("이름이 공백뿐이면 MADE_DEX_NAME_REQUIRED, 저장하지 않는다")
    void create_blankName() {
        assertThatThrownBy(() -> madeDexService.create(
                OWNER_ID, new MadeDexCreateRequestDTO("   ", null, Visibility.PRIVATE, null)))
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
                OWNER_ID, new MadeDexCreateRequestDTO(name, null, Visibility.PRIVATE, null)))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MADE_DEX_NAME_TOO_LONG));

        verify(madeDexRepository, never()).save(any());
    }

    @Test
    @DisplayName("소개가 500자를 넘으면 MADE_DEX_DESCRIPTION_TOO_LONG")
    void create_descriptionTooLong() {
        String description = "가".repeat(MadeDex.DESCRIPTION_MAX + 1);

        assertThatThrownBy(() -> madeDexService.create(
                OWNER_ID, new MadeDexCreateRequestDTO("우리 도감", description, Visibility.PRIVATE, null)))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MADE_DEX_DESCRIPTION_TOO_LONG));

        verify(madeDexRepository, never()).save(any());
    }

    @Test
    @DisplayName("표지 이미지 key를 그대로 저장한다")
    void create_keepsImageKey() {
        when(madeDexRepository.save(any())).thenAnswer(invocation -> {
            MadeDex madeDex = invocation.getArgument(0);
            ReflectionTestUtils.setField(madeDex, "id", 10L);
            return madeDex;
        });

        madeDexService.create(
                OWNER_ID, new MadeDexCreateRequestDTO("우리 도감", null, null, "made/2026/08/05/cover.jpg"));

        ArgumentCaptor<MadeDex> captor = ArgumentCaptor.forClass(MadeDex.class);
        verify(madeDexRepository).save(captor.capture());
        assertThat(captor.getValue().getImageKey()).isEqualTo("made/2026/08/05/cover.jpg");
    }

    @Test
    @DisplayName("이미지를 안 고르면 표지 없이 개설된다")
    void create_withoutImage() {
        when(madeDexRepository.save(any())).thenAnswer(invocation -> {
            MadeDex madeDex = invocation.getArgument(0);
            ReflectionTestUtils.setField(madeDex, "id", 10L);
            return madeDex;
        });

        madeDexService.create(OWNER_ID, new MadeDexCreateRequestDTO("우리 도감", null, null, "  "));

        ArgumentCaptor<MadeDex> captor = ArgumentCaptor.forClass(MadeDex.class);
        verify(madeDexRepository).save(captor.capture());
        assertThat(captor.getValue().getImageKey()).isNull();
    }

    @Test
    @DisplayName("공개 도감은 참여하지 않아도 상세를 볼 수 있다")
    void findDetail_publicAllowsNonMember() {
        when(madeDexRepository.findByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(savedMadeDex(10L, "회사 점심 도감", Visibility.PUBLIC)));
        when(madeDexMemberRepository.findByMadeDexIdAndUserId(10L, STRANGER_ID))
                .thenReturn(Optional.empty());
        when(madeDexMemberRepository.countByMadeDexId(10L)).thenReturn(4L);

        MadeDexDetailDTO detail = madeDexService.findDetail(STRANGER_ID, 10L);

        assertThat(detail.memberCount()).isEqualTo(4L);
        assertThat(detail.myRole()).isNull();
    }

    @Test
    @DisplayName("비공개 도감은 멤버가 아니면 존재를 알리지 않는다")
    void findDetail_privateHidesFromNonMember() {
        when(madeDexRepository.findByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(savedMadeDex(10L, "우리 도감", Visibility.PRIVATE)));
        when(madeDexMemberRepository.findByMadeDexIdAndUserId(10L, STRANGER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> madeDexService.findDetail(STRANGER_ID, 10L))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MADE_DEX_NOT_FOUND));
    }

    @Test
    @DisplayName("비공개 도감도 멤버는 볼 수 있다")
    void findDetail_privateAllowsMember() {
        when(madeDexRepository.findByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(savedMadeDex(10L, "우리 도감", Visibility.PRIVATE)));
        when(madeDexMemberRepository.findByMadeDexIdAndUserId(10L, OWNER_ID))
                .thenReturn(Optional.of(membership(10L, MadeDexRole.OWNER)));
        when(madeDexMemberRepository.countByMadeDexId(10L)).thenReturn(1L);

        assertThat(madeDexService.findDetail(OWNER_ID, 10L).myRole()).isEqualTo(MadeDexRole.OWNER);
    }

    @Test
    @DisplayName("수정하면 보낸 값으로 전부 교체된다")
    void update_replacesAllFields() {
        MadeDex madeDex = savedMadeDex(10L, "우리 도감", Visibility.PRIVATE);
        when(madeDexRepository.findActiveByIdForUpdate(10L)).thenReturn(Optional.of(madeDex));

        madeDexService.update(OWNER_ID, 10L,
                new MadeDexUpdateRequestDTO("새 이름", "새 소개", Visibility.PUBLIC, "made/new.jpg"));

        assertThat(madeDex.getName()).isEqualTo("새 이름");
        assertThat(madeDex.getDescription()).isEqualTo("새 소개");
        assertThat(madeDex.getVisibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(madeDex.getImageKey()).isEqualTo("made/new.jpg");
    }

    @Test
    @DisplayName("소개말을 비우면 지워진다")
    void update_clearsDescription() {
        MadeDex madeDex = MadeDex.open(OWNER_ID, "우리 도감", "옛 소개", Visibility.PRIVATE, null);
        ReflectionTestUtils.setField(madeDex, "id", 10L);
        when(madeDexRepository.findActiveByIdForUpdate(10L)).thenReturn(Optional.of(madeDex));

        madeDexService.update(OWNER_ID, 10L,
                new MadeDexUpdateRequestDTO("우리 도감", null, Visibility.PRIVATE, null));

        assertThat(madeDex.getDescription()).isNull();
    }

    @Test
    @DisplayName("표지를 바꾸면 이전 객체 삭제를 커밋 이후로 미룬다")
    void update_defersReplacedImageDeletion() {
        MadeDex madeDex = MadeDex.open(OWNER_ID, "우리 도감", null, Visibility.PRIVATE, "made/old.jpg");
        ReflectionTestUtils.setField(madeDex, "id", 10L);
        when(madeDexRepository.findActiveByIdForUpdate(10L)).thenReturn(Optional.of(madeDex));

        madeDexService.update(OWNER_ID, 10L,
                new MadeDexUpdateRequestDTO("우리 도감", null, Visibility.PRIVATE, "made/new.jpg"));

        verify(eventPublisher).publishEvent(new S3ObjectUnusedEvent("made/old.jpg"));
        // 트랜잭션 안에서 지우면 뒤이어 롤백됐을 때 되살릴 수 없다
        verify(s3PresignedUrlService, never()).deleteObject(any());
    }

    @Test
    @DisplayName("표지를 비우면 이전 객체 삭제를 커밋 이후로 미룬다")
    void update_defersClearedImageDeletion() {
        MadeDex madeDex = MadeDex.open(OWNER_ID, "우리 도감", null, Visibility.PRIVATE, "made/old.jpg");
        ReflectionTestUtils.setField(madeDex, "id", 10L);
        when(madeDexRepository.findActiveByIdForUpdate(10L)).thenReturn(Optional.of(madeDex));

        madeDexService.update(OWNER_ID, 10L,
                new MadeDexUpdateRequestDTO("우리 도감", null, Visibility.PRIVATE, null));

        verify(eventPublisher).publishEvent(new S3ObjectUnusedEvent("made/old.jpg"));
    }

    @Test
    @DisplayName("표지를 그대로 두면 삭제를 예약하지 않는다")
    void update_keepsSameImage() {
        MadeDex madeDex = MadeDex.open(OWNER_ID, "우리 도감", null, Visibility.PRIVATE, "made/old.jpg");
        ReflectionTestUtils.setField(madeDex, "id", 10L);
        when(madeDexRepository.findActiveByIdForUpdate(10L)).thenReturn(Optional.of(madeDex));

        madeDexService.update(OWNER_ID, 10L,
                new MadeDexUpdateRequestDTO("우리 도감", null, Visibility.PRIVATE, "made/old.jpg"));

        verify(eventPublisher, never()).publishEvent(any(S3ObjectUnusedEvent.class));
    }

    @Test
    @DisplayName("그룹장이 아니면 수정할 수 없다")
    void update_notOwner() {
        when(madeDexRepository.findActiveByIdForUpdate(10L))
                .thenReturn(Optional.of(savedMadeDex(10L, "우리 도감", Visibility.PRIVATE)));

        assertThatThrownBy(() -> madeDexService.update(STRANGER_ID, 10L,
                new MadeDexUpdateRequestDTO("새 이름", null, Visibility.PRIVATE, null)))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MADE_DEX_NOT_OWNER));
    }

    @Test
    @DisplayName("수정할 때도 이름은 비울 수 없다")
    void update_blankName() {
        when(madeDexRepository.findActiveByIdForUpdate(10L))
                .thenReturn(Optional.of(savedMadeDex(10L, "우리 도감", Visibility.PRIVATE)));

        assertThatThrownBy(() -> madeDexService.update(OWNER_ID, 10L,
                new MadeDexUpdateRequestDTO("   ", null, Visibility.PRIVATE, null)))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MADE_DEX_NAME_REQUIRED));
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
