package com.backend_catcheat.domain.made.service;

import com.backend_catcheat.domain.auth.entity.User;
import com.backend_catcheat.domain.auth.repository.UserRepository;
import com.backend_catcheat.domain.made.dto.MadeDexFeedCardDTO;
import com.backend_catcheat.domain.made.dto.MadeDexFeedDTO;
import com.backend_catcheat.domain.made.dto.MadeDexFeedSlotDTO;
import com.backend_catcheat.domain.made.entity.MadeDex;
import com.backend_catcheat.domain.made.entity.MadeDexMember;
import com.backend_catcheat.domain.made.entity.MadeDexRecord;
import com.backend_catcheat.domain.made.entity.MadeDexRecordFood;
import com.backend_catcheat.domain.made.entity.MadeDexRecordPhoto;
import com.backend_catcheat.domain.made.entity.MadeDexRole;
import com.backend_catcheat.domain.made.entity.MadeDexSlot;
import com.backend_catcheat.domain.made.entity.Visibility;
import com.backend_catcheat.domain.made.repository.MadeDexMemberRepository;
import com.backend_catcheat.domain.made.repository.MadeDexRecordFoodRepository;
import com.backend_catcheat.domain.made.repository.MadeDexRecordPhotoRepository;
import com.backend_catcheat.domain.made.repository.MadeDexRecordRepository;
import com.backend_catcheat.domain.made.repository.MadeDexSlotRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import com.backend_catcheat.global.s3.S3PresignedUrlService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MadeDexFeedServiceTest {

    private static final long ME = 1L;
    private static final long FRIEND = 2L;
    private static final long MADE_DEX_ID = 10L;
    private static final long BREAKFAST = 20L;
    private static final long LUNCH = 21L;

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 6, 23, 30);
    private static final LocalDate TODAY_SEOUL = LocalDate.of(2026, 8, 7);

    private final Clock clock = Clock.fixed(NOW.atZone(UTC).toInstant(), UTC);

    @Mock MadeDexSlotRepository madeDexSlotRepository;
    @Mock MadeDexMemberRepository madeDexMemberRepository;
    @Mock MadeDexRecordRepository madeDexRecordRepository;
    @Mock MadeDexRecordPhotoRepository madeDexRecordPhotoRepository;
    @Mock MadeDexRecordFoodRepository madeDexRecordFoodRepository;
    @Mock MadeDexFinder madeDexFinder;
    @Mock UserRepository userRepository;
    @Mock S3PresignedUrlService s3PresignedUrlService;

    MadeDexFeedService service;

    @BeforeEach
    void setUp() {
        service = new MadeDexFeedService(
                madeDexSlotRepository, madeDexMemberRepository, madeDexRecordRepository,
                madeDexRecordPhotoRepository, madeDexRecordFoodRepository,
                madeDexFinder, userRepository, s3PresignedUrlService, clock);

        when(madeDexFinder.readable(ME, MADE_DEX_ID))
                .thenReturn(new MadeDexFinder.MadeDexAccess(madeDex(), MadeDexRole.MEMBER));
        // 나는 나중에 들어왔지만 내 카드가 먼저 와야 한다
        when(madeDexMemberRepository.findByMadeDexIdOrderByJoinedAtAscIdAsc(MADE_DEX_ID))
                .thenReturn(List.of(
                        MadeDexMember.member(MADE_DEX_ID, FRIEND, NOW.minusDays(2)),
                        MadeDexMember.member(MADE_DEX_ID, ME, NOW.minusDays(1))));
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(user(ME, "나"), user(FRIEND, "지민")));
        when(madeDexSlotRepository.findByMadeDexIdOrderBySortOrderAscIdAsc(MADE_DEX_ID))
                .thenReturn(List.of(slot(BREAKFAST, "아침", 0, false), slot(LUNCH, "점심", 1, false)));
        when(madeDexRecordRepository
                .findByMadeDexIdAndLoggedOnAndDeletedAtIsNullOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of());
        when(madeDexRecordPhotoRepository.findByRecordIdInOrderBySortOrderAsc(anyCollection()))
                .thenReturn(List.of());
        when(madeDexRecordFoodRepository.findByRecordIdInOrderBySortOrderAsc(anyCollection()))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("내 카드가 항상 첫 장이다 — 가입 순서와 무관하다")
    void 내_카드가_먼저다() {
        MadeDexFeedDTO feed = service.findFeed(ME, MADE_DEX_ID, TODAY_SEOUL);

        List<MadeDexFeedCardDTO> cards = feed.slots().getFirst().cards();
        assertThat(cards.getFirst().userId()).isEqualTo(ME);
        assertThat(cards.getFirst().me()).isTrue();
        assertThat(cards.get(1).userId()).isEqualTo(FRIEND);
    }

    @Test
    @DisplayName("기록이 없는 멤버도 빈 카드로 자리를 남긴다")
    void 미기록_멤버도_카드를_받는다() {
        MadeDexFeedDTO feed = service.findFeed(ME, MADE_DEX_ID, TODAY_SEOUL);

        assertThat(feed.slots()).hasSize(2);
        assertThat(feed.slots()).allSatisfy(slot ->
                assertThat(slot.cards()).hasSize(2).allSatisfy(card -> {
                    assertThat(card.recordCount()).isZero();
                    assertThat(card.thumbnailUrl()).isNull();
                    assertThat(card.recordIds()).isEmpty();
                }));
    }

    @Test
    @DisplayName("기록은 슬롯과 작성자에 맞춰 들어간다")
    void 기록이_제자리에_들어간다() {
        when(madeDexRecordRepository
                .findByMadeDexIdAndLoggedOnAndDeletedAtIsNullOrderByCreatedAtAsc(MADE_DEX_ID, TODAY_SEOUL))
                .thenReturn(List.of(record(100L, BREAKFAST, ME)));
        when(madeDexRecordPhotoRepository.findByRecordIdInOrderBySortOrderAsc(anyCollection()))
                .thenReturn(List.of(MadeDexRecordPhoto.of(100L, "key1", 0),
                        MadeDexRecordPhoto.of(100L, "key2", 1)));
        when(madeDexRecordFoodRepository.findByRecordIdInOrderBySortOrderAsc(anyCollection()))
                .thenReturn(List.of(MadeDexRecordFood.of(100L, "계란 토스트", 0)));

        MadeDexFeedDTO feed = service.findFeed(ME, MADE_DEX_ID, TODAY_SEOUL);

        MadeDexFeedCardDTO mine = feed.slots().getFirst().cards().getFirst();
        assertThat(mine.recordCount()).isEqualTo(1);
        assertThat(mine.foodNames()).containsExactly("계란 토스트");
        assertThat(mine.recordIds()).containsExactly(100L);
        // 친구 카드와 점심 슬롯은 비어 있어야 한다
        assertThat(feed.slots().getFirst().cards().get(1).recordCount()).isZero();
        assertThat(feed.slots().get(1).cards()).allSatisfy(card ->
                assertThat(card.recordCount()).isZero());
    }

    @Test
    @DisplayName("같은 슬롯의 기록 여러 건을 카드 한 장으로 접는다")
    void 여러_기록을_한_카드로_접는다() {
        when(madeDexRecordRepository
                .findByMadeDexIdAndLoggedOnAndDeletedAtIsNullOrderByCreatedAtAsc(MADE_DEX_ID, TODAY_SEOUL))
                .thenReturn(List.of(record(100L, BREAKFAST, ME), record(101L, BREAKFAST, ME)));
        when(madeDexRecordPhotoRepository.findByRecordIdInOrderBySortOrderAsc(anyCollection()))
                .thenReturn(List.of(MadeDexRecordPhoto.of(100L, "first", 0),
                        MadeDexRecordPhoto.of(101L, "second", 0)));
        when(madeDexRecordFoodRepository.findByRecordIdInOrderBySortOrderAsc(anyCollection()))
                .thenReturn(List.of(MadeDexRecordFood.of(100L, "계란 토스트", 0),
                        MadeDexRecordFood.of(101L, "그릭요거트 볼", 0)));
        when(s3PresignedUrlService.createDownloadUrl("first")).thenReturn("url-first");

        MadeDexFeedCardDTO mine = service.findFeed(ME, MADE_DEX_ID, TODAY_SEOUL)
                .slots().getFirst().cards().getFirst();

        assertThat(mine.recordCount()).isEqualTo(2);
        // 대표 사진은 먼저 남긴 기록의 첫 장이다
        assertThat(mine.thumbnailUrl()).isEqualTo("url-first");
        assertThat(mine.foodNames()).containsExactly("계란 토스트", "그릭요거트 볼");
        assertThat(mine.recordIds()).containsExactly(100L, 101L);
    }

    @Test
    @DisplayName("숨긴 슬롯은 빠지지만, 그날 기록이 있으면 보여 준다")
    void 기록이_있는_숨긴_슬롯은_남는다() {
        MadeDexSlot hiddenLunch = slot(LUNCH, "점심", 1, true);
        when(madeDexSlotRepository.findByMadeDexIdOrderBySortOrderAscIdAsc(MADE_DEX_ID))
                .thenReturn(List.of(slot(BREAKFAST, "아침", 0, false), hiddenLunch));

        assertThat(service.findFeed(ME, MADE_DEX_ID, TODAY_SEOUL).slots())
                .extracting(MadeDexFeedSlotDTO::slotId)
                .containsExactly(BREAKFAST);

        when(madeDexRecordRepository
                .findByMadeDexIdAndLoggedOnAndDeletedAtIsNullOrderByCreatedAtAsc(MADE_DEX_ID, TODAY_SEOUL))
                .thenReturn(List.of(record(100L, LUNCH, ME)));

        assertThat(service.findFeed(ME, MADE_DEX_ID, TODAY_SEOUL).slots())
                .extracting(MadeDexFeedSlotDTO::slotId)
                .containsExactly(BREAKFAST, LUNCH);
    }

    @Test
    @DisplayName("날짜를 안 주면 서울 기준 오늘이다")
    void 기본값은_서울_기준_오늘이다() {
        assertThat(service.findFeed(ME, MADE_DEX_ID, null).date()).isEqualTo(TODAY_SEOUL);
    }

    @Test
    @DisplayName("아직 오지 않은 날은 열 수 없다")
    void 미래_날짜는_거절한다() {
        assertThatThrownBy(() -> service.findFeed(ME, MADE_DEX_ID, TODAY_SEOUL.plusDays(1)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_RECORD_FUTURE_DATE);
    }

    private MadeDex madeDex() {
        MadeDex madeDex = MadeDex.open(ME, "우리 식탁", null, Visibility.PRIVATE, null);
        ReflectionTestUtils.setField(madeDex, "id", MADE_DEX_ID);
        return madeDex;
    }

    private MadeDexSlot slot(Long id, String name, int sortOrder, boolean hidden) {
        MadeDexSlot slot = MadeDexSlot.of(MADE_DEX_ID, name, sortOrder);
        ReflectionTestUtils.setField(slot, "id", id);
        if (hidden) {
            slot.hide(NOW);
        }
        return slot;
    }

    private MadeDexRecord record(Long id, Long slotId, Long authorId) {
        MadeDexRecord record = MadeDexRecord.write(
                MADE_DEX_ID, slotId, authorId, TODAY_SEOUL, null, null, null, null);
        ReflectionTestUtils.setField(record, "id", id);
        return record;
    }

    private User user(Long id, String nickname) {
        User user = User.builder().nickname(nickname).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
