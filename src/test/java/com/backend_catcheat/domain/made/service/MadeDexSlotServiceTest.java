package com.backend_catcheat.domain.made.service;

import com.backend_catcheat.domain.made.dto.MadeDexSlotDTO;
import com.backend_catcheat.domain.made.dto.MadeDexSlotDeleteResponseDTO;
import com.backend_catcheat.domain.made.entity.MadeDex;
import com.backend_catcheat.domain.made.entity.MadeDexRole;
import com.backend_catcheat.domain.made.entity.MadeDexSlot;
import com.backend_catcheat.domain.made.entity.Visibility;
import com.backend_catcheat.domain.made.repository.MadeDexMemberRepository;
import com.backend_catcheat.domain.made.repository.MadeDexSlotRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
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

// 숨김 시각을 sleep 없이 검증하려고 고정 시계를 쓴다
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MadeDexSlotServiceTest {

    private static final long MEMBER_ID = 1L;
    private static final long STRANGER_ID = 2L;
    private static final long MADE_DEX_ID = 10L;
    private static final long OTHER_DEX_ID = 11L;

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 6, 12, 0);

    private final Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    @Mock
    MadeDexSlotRepository madeDexSlotRepository;
    @Mock
    MadeDexMemberRepository madeDexMemberRepository;
    @Mock
    MadeDexFinder madeDexFinder;

    MadeDexSlotService service;

    @BeforeEach
    void setUp() {
        service = new MadeDexSlotService(
                madeDexSlotRepository, madeDexMemberRepository, madeDexFinder, clock);

        when(madeDexFinder.locked(MADE_DEX_ID)).thenReturn(madeDex(Visibility.PRIVATE));
        when(madeDexFinder.readable(MEMBER_ID, MADE_DEX_ID))
                .thenReturn(new MadeDexFinder.MadeDexAccess(madeDex(Visibility.PRIVATE), MadeDexRole.MEMBER));
        when(madeDexMemberRepository.existsByMadeDexIdAndUserId(MADE_DEX_ID, MEMBER_ID)).thenReturn(true);
        when(madeDexMemberRepository.existsByMadeDexIdAndUserId(MADE_DEX_ID, STRANGER_ID)).thenReturn(false);
        when(madeDexSlotRepository.findSlotIdsWithRecords(anyLong())).thenReturn(List.of());
        when(madeDexSlotRepository.save(any(MadeDexSlot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }


    @Test
    @DisplayName("그룹장이 아닌 일반 멤버도 슬롯을 추가할 수 있다")
    void 일반_멤버도_슬롯을_추가한다() {
        when(madeDexSlotRepository.countByMadeDexIdAndHiddenAtIsNull(MADE_DEX_ID)).thenReturn(3L);
        when(madeDexSlotRepository.findByMadeDexIdAndHiddenAtIsNullOrderBySortOrderAscIdAsc(MADE_DEX_ID))
                .thenReturn(List.of(slot(1L, "아침", 0), slot(2L, "점심", 1), slot(3L, "저녁", 2)));

        MadeDexSlotDTO added = service.add(MEMBER_ID, MADE_DEX_ID, "야식");

        assertThat(added.name()).isEqualTo("야식");
        assertThat(added.sortOrder()).isEqualTo(3);
    }

    @Test
    @DisplayName("멤버가 아니면 슬롯을 편집할 수 없다")
    void 비멤버는_편집할_수_없다() {
        assertThatThrownBy(() -> service.add(STRANGER_ID, MADE_DEX_ID, "야식"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_NOT_MEMBER);
    }


    @Test
    @DisplayName("슬롯이 6개면 더 추가할 수 없다")
    void 최대_6개를_넘길_수_없다() {
        when(madeDexSlotRepository.countByMadeDexIdAndHiddenAtIsNull(MADE_DEX_ID)).thenReturn(6L);

        assertThatThrownBy(() -> service.add(MEMBER_ID, MADE_DEX_ID, "야식"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_SLOT_LIMIT_EXCEEDED);
        verify(madeDexSlotRepository, never()).save(any());
    }

    @Test
    @DisplayName("마지막 남은 슬롯은 없앨 수 없다")
    void 마지막_슬롯은_지울_수_없다() {
        when(madeDexSlotRepository.countByMadeDexIdAndHiddenAtIsNull(MADE_DEX_ID)).thenReturn(1L);
        when(madeDexSlotRepository.findById(1L)).thenReturn(Optional.of(slot(1L, "아침", 0)));

        assertThatThrownBy(() -> service.delete(MEMBER_ID, MADE_DEX_ID, 1L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_SLOT_LAST_ONE);
    }


    @Test
    @DisplayName("같은 이름의 슬롯은 만들 수 없다")
    void 이름이_겹치면_거절한다() {
        when(madeDexSlotRepository.countByMadeDexIdAndHiddenAtIsNull(MADE_DEX_ID)).thenReturn(3L);
        when(madeDexSlotRepository.existsByMadeDexIdAndNameAndHiddenAtIsNull(MADE_DEX_ID, "아침"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.add(MEMBER_ID, MADE_DEX_ID, "아침"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_SLOT_NAME_DUPLICATED);
    }

    @Test
    @DisplayName("이름을 그대로 둔 채 저장해도 중복으로 막지 않는다")
    void 같은_이름으로_다시_저장해도_통과한다() {
        MadeDexSlot slot = slot(1L, "아침", 0);
        when(madeDexSlotRepository.findById(1L)).thenReturn(Optional.of(slot));
        when(madeDexSlotRepository.existsByMadeDexIdAndNameAndHiddenAtIsNull(MADE_DEX_ID, "아침"))
                .thenReturn(true);

        MadeDexSlotDTO renamed = service.rename(MEMBER_ID, MADE_DEX_ID, 1L, "아침");

        assertThat(renamed.name()).isEqualTo("아침");
    }

    @Test
    @DisplayName("이름을 바꿀 때도 그룹 행을 잠근다")
    void 이름_변경도_잠금을_잡는다() {
        when(madeDexSlotRepository.findById(1L)).thenReturn(Optional.of(slot(1L, "아침", 0)));

        service.rename(MEMBER_ID, MADE_DEX_ID, 1L, "브런치");

        verify(madeDexFinder).locked(MADE_DEX_ID);
    }

    @Test
    @DisplayName("빈 이름은 거절한다")
    void 빈_이름은_거절한다() {
        assertThatThrownBy(() -> service.add(MEMBER_ID, MADE_DEX_ID, "  "))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_SLOT_NAME_REQUIRED);
    }

    @Test
    @DisplayName("20자를 넘는 이름은 거절한다")
    void 너무_긴_이름은_거절한다() {
        String tooLong = "가".repeat(MadeDexSlot.NAME_MAX + 1);

        assertThatThrownBy(() -> service.add(MEMBER_ID, MADE_DEX_ID, tooLong))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_SLOT_NAME_TOO_LONG);
    }


    @Test
    @DisplayName("기록이 있는 슬롯은 지워지지 않고 숨겨진다")
    void 기록이_있으면_숨긴다() {
        MadeDexSlot slot = slot(2L, "점심", 1);
        when(madeDexSlotRepository.countByMadeDexIdAndHiddenAtIsNull(MADE_DEX_ID)).thenReturn(3L);
        when(madeDexSlotRepository.findById(2L)).thenReturn(Optional.of(slot));
        when(madeDexSlotRepository.countRecords(2L)).thenReturn(4L);
        when(madeDexSlotRepository.existsRecordReferencing(2L)).thenReturn(true);

        MadeDexSlotDeleteResponseDTO result = service.delete(MEMBER_ID, MADE_DEX_ID, 2L);

        assertThat(result.hidden()).isTrue();
        assertThat(slot.getHiddenAt()).isEqualTo(NOW);
        verify(madeDexSlotRepository, never()).delete(any());
    }

    @Test
    @DisplayName("작성자가 지운 기록만 남은 슬롯도 지우지 않고 숨긴다")
    void 소프트_삭제된_기록만_있어도_숨긴다() {
        MadeDexSlot slot = slot(2L, "점심", 1);
        when(madeDexSlotRepository.countByMadeDexIdAndHiddenAtIsNull(MADE_DEX_ID)).thenReturn(3L);
        when(madeDexSlotRepository.findById(2L)).thenReturn(Optional.of(slot));
        when(madeDexSlotRepository.countRecords(2L)).thenReturn(0L);
        when(madeDexSlotRepository.existsRecordReferencing(2L)).thenReturn(true);

        MadeDexSlotDeleteResponseDTO result = service.delete(MEMBER_ID, MADE_DEX_ID, 2L);

        assertThat(result.hidden()).isTrue();
        verify(madeDexSlotRepository, never()).delete(any());
    }

    @Test
    @DisplayName("기록이 없는 슬롯은 완전히 지운다")
    void 기록이_없으면_삭제한다() {
        MadeDexSlot slot = slot(2L, "점심", 1);
        when(madeDexSlotRepository.countByMadeDexIdAndHiddenAtIsNull(MADE_DEX_ID)).thenReturn(3L);
        when(madeDexSlotRepository.findById(2L)).thenReturn(Optional.of(slot));
        when(madeDexSlotRepository.countRecords(2L)).thenReturn(0L);

        MadeDexSlotDeleteResponseDTO result = service.delete(MEMBER_ID, MADE_DEX_ID, 2L);

        assertThat(result.hidden()).isFalse();
        verify(madeDexSlotRepository).delete(slot);
    }

    @Test
    @DisplayName("숨긴 슬롯을 되살리면 맨 뒤 순서로 돌아온다")
    void 숨긴_슬롯을_되살린다() {
        MadeDexSlot slot = slot(2L, "점심", 1);
        slot.hide(NOW);
        when(madeDexSlotRepository.findById(2L)).thenReturn(Optional.of(slot));
        when(madeDexSlotRepository.countByMadeDexIdAndHiddenAtIsNull(MADE_DEX_ID)).thenReturn(2L);
        when(madeDexSlotRepository.findByMadeDexIdAndHiddenAtIsNullOrderBySortOrderAscIdAsc(MADE_DEX_ID))
                .thenReturn(List.of(slot(1L, "아침", 0), slot(3L, "저녁", 1)));

        MadeDexSlotDTO restored = service.restore(MEMBER_ID, MADE_DEX_ID, 2L);

        assertThat(restored.hidden()).isFalse();
        assertThat(restored.sortOrder()).isEqualTo(2);
    }


    @Test
    @DisplayName("보낸 순서대로 sortOrder를 다시 매긴다")
    void 순서를_바꾼다() {
        MadeDexSlot breakfast = slot(1L, "아침", 0);
        MadeDexSlot lunch = slot(2L, "점심", 1);
        MadeDexSlot dinner = slot(3L, "저녁", 2);
        when(madeDexSlotRepository.findByMadeDexIdAndHiddenAtIsNullOrderBySortOrderAscIdAsc(MADE_DEX_ID))
                .thenReturn(List.of(breakfast, lunch, dinner));

        List<MadeDexSlotDTO> result = service.reorder(MEMBER_ID, MADE_DEX_ID, List.of(3L, 1L, 2L));

        assertThat(result).extracting(MadeDexSlotDTO::name)
                .containsExactly("저녁", "아침", "점심");
        assertThat(dinner.getSortOrder()).isZero();
    }

    @Test
    @DisplayName("목록이 어긋나면 순서를 반영하지 않는다")
    void 목록이_어긋나면_거절한다() {
        when(madeDexSlotRepository.findByMadeDexIdAndHiddenAtIsNullOrderBySortOrderAscIdAsc(MADE_DEX_ID))
                .thenReturn(List.of(slot(1L, "아침", 0), slot(2L, "점심", 1), slot(3L, "저녁", 2)));

        // 그 사이 누군가 슬롯을 하나 더 만들었다
        assertThatThrownBy(() -> service.reorder(MEMBER_ID, MADE_DEX_ID, List.of(2L, 1L)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_SLOT_ORDER_MISMATCH);
    }

    @Test
    @DisplayName("같은 id를 두 번 보내면 거절한다")
    void 중복_id는_거절한다() {
        when(madeDexSlotRepository.findByMadeDexIdAndHiddenAtIsNullOrderBySortOrderAscIdAsc(MADE_DEX_ID))
                .thenReturn(List.of(slot(1L, "아침", 0), slot(2L, "점심", 1)));

        assertThatThrownBy(() -> service.reorder(MEMBER_ID, MADE_DEX_ID, List.of(1L, 1L)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_SLOT_ORDER_MISMATCH);
    }


    @Test
    @DisplayName("다른 그룹의 슬롯 id는 찾을 수 없다고 답한다")
    void 남의_슬롯은_보이지_않는다() {
        MadeDexSlot otherSlot = MadeDexSlot.of(OTHER_DEX_ID, "아침", 0);
        ReflectionTestUtils.setField(otherSlot, "id", 99L);
        when(madeDexSlotRepository.findById(99L)).thenReturn(Optional.of(otherSlot));

        assertThatThrownBy(() -> service.rename(MEMBER_ID, MADE_DEX_ID, 99L, "브런치"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_SLOT_NOT_FOUND);
    }

    @Test
    @DisplayName("열람할 수 없는 그룹이면 슬롯을 조회하지 않는다")
    void 열람_불가면_조회하지_않는다() {
        when(madeDexFinder.readable(STRANGER_ID, MADE_DEX_ID))
                .thenThrow(new CustomException(ErrorCode.MADE_DEX_NOT_FOUND));

        assertThatThrownBy(() -> service.findSlots(STRANGER_ID, MADE_DEX_ID))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_NOT_FOUND);
        verify(madeDexSlotRepository, never()).findByMadeDexIdOrderBySortOrderAscIdAsc(anyLong());
    }

    @Test
    @DisplayName("공개 그룹의 슬롯은 참여하지 않아도 볼 수 있다")
    void 공개_그룹은_누구나_본다() {
        when(madeDexFinder.readable(STRANGER_ID, MADE_DEX_ID))
                .thenReturn(new MadeDexFinder.MadeDexAccess(madeDex(Visibility.PUBLIC), null));
        when(madeDexSlotRepository.findByMadeDexIdOrderBySortOrderAscIdAsc(MADE_DEX_ID))
                .thenReturn(List.of(slot(1L, "아침", 0)));

        List<MadeDexSlotDTO> slots = service.findSlots(STRANGER_ID, MADE_DEX_ID);

        assertThat(slots).hasSize(1);
    }

    @Test
    @DisplayName("숨긴 슬롯도 목록에 내려간다")
    void 숨긴_슬롯도_내려간다() {
        MadeDexSlot hidden = slot(2L, "점심", 1);
        hidden.hide(NOW);
        when(madeDexSlotRepository.findByMadeDexIdOrderBySortOrderAscIdAsc(MADE_DEX_ID))
                .thenReturn(List.of(slot(1L, "아침", 0), hidden));

        List<MadeDexSlotDTO> slots = service.findSlots(MEMBER_ID, MADE_DEX_ID);

        assertThat(slots).extracting(MadeDexSlotDTO::hidden).containsExactly(false, true);
    }


    @Test
    @DisplayName("그룹 개설 시 만들 기본 슬롯은 아침·점심·저녁 순서다")
    void 기본_슬롯은_세_개다() {
        List<MadeDexSlot> defaults = MadeDexSlot.defaultsFor(MADE_DEX_ID);

        assertThat(defaults).extracting(MadeDexSlot::getName)
                .containsExactly("아침", "점심", "저녁");
        assertThat(defaults).extracting(MadeDexSlot::getSortOrder)
                .containsExactly(0, 1, 2);
    }

    private MadeDex madeDex(Visibility visibility) {
        MadeDex madeDex = MadeDex.open(MEMBER_ID, "우리 식탁", null, visibility, null);
        ReflectionTestUtils.setField(madeDex, "id", MADE_DEX_ID);
        return madeDex;
    }

    private MadeDexSlot slot(Long id, String name, int sortOrder) {
        MadeDexSlot slot = MadeDexSlot.of(MADE_DEX_ID, name, sortOrder);
        ReflectionTestUtils.setField(slot, "id", id);
        return slot;
    }
}
