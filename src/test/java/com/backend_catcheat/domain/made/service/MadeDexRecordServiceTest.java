package com.backend_catcheat.domain.made.service;

import com.backend_catcheat.domain.auth.repository.UserRepository;
import com.backend_catcheat.domain.made.dto.MadeDexRecordCreateRequestDTO;
import com.backend_catcheat.domain.made.dto.MadeDexRecordUpdateRequestDTO;
import com.backend_catcheat.domain.made.entity.MadeDex;
import com.backend_catcheat.domain.made.entity.MadeDexRecord;
import com.backend_catcheat.domain.made.entity.MadeDexRecordPhoto;
import com.backend_catcheat.domain.made.entity.MadeDexSlot;
import com.backend_catcheat.domain.made.entity.Visibility;
import com.backend_catcheat.domain.made.repository.MadeDexMemberRepository;
import com.backend_catcheat.domain.made.repository.MadeDexRecordFoodRepository;
import com.backend_catcheat.domain.made.repository.MadeDexRecordPhotoRepository;
import com.backend_catcheat.domain.made.repository.MadeDexRecordRepository;
import com.backend_catcheat.domain.made.repository.MadeDexSlotRepository;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 미래 날짜 판정을 sleep 없이 검증하려고 고정 시계를 쓴다
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MadeDexRecordServiceTest {

    private static final long AUTHOR_ID = 1L;
    private static final long OTHER_MEMBER_ID = 2L;
    private static final long STRANGER_ID = 3L;
    private static final long MADE_DEX_ID = 10L;
    private static final long OTHER_DEX_ID = 11L;
    private static final long SLOT_ID = 20L;
    private static final long RECORD_ID = 30L;

    private static final ZoneId ZONE = ZoneId.systemDefault();
    // 한국 시각으로는 8월 7일 오전이지만 UTC로는 아직 8월 6일인 순간
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 6, 23, 30);
    private static final LocalDate TODAY_SEOUL = LocalDate.of(2026, 8, 7);

    private final Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC"));

    @Mock MadeDexRecordRepository madeDexRecordRepository;
    @Mock MadeDexRecordPhotoRepository madeDexRecordPhotoRepository;
    @Mock MadeDexRecordFoodRepository madeDexRecordFoodRepository;
    @Mock MadeDexSlotRepository madeDexSlotRepository;
    @Mock MadeDexMemberRepository madeDexMemberRepository;
    @Mock MadeDexFinder madeDexFinder;
    @Mock UserRepository userRepository;
    @Mock S3PresignedUrlService s3PresignedUrlService;
    @Mock ApplicationEventPublisher eventPublisher;

    MadeDexRecordService service;

    @BeforeEach
    void setUp() {
        service = new MadeDexRecordService(
                madeDexRecordRepository, madeDexRecordPhotoRepository, madeDexRecordFoodRepository,
                madeDexSlotRepository, madeDexMemberRepository, madeDexFinder,
                userRepository, s3PresignedUrlService, eventPublisher, clock);

        when(madeDexFinder.active(MADE_DEX_ID)).thenReturn(madeDex());
        when(madeDexMemberRepository.existsByMadeDexIdAndUserId(MADE_DEX_ID, AUTHOR_ID)).thenReturn(true);
        when(madeDexMemberRepository.existsByMadeDexIdAndUserId(MADE_DEX_ID, OTHER_MEMBER_ID)).thenReturn(true);
        when(madeDexMemberRepository.existsByMadeDexIdAndUserId(MADE_DEX_ID, STRANGER_ID)).thenReturn(false);
        when(madeDexSlotRepository.findById(SLOT_ID)).thenReturn(Optional.of(slot(SLOT_ID, MADE_DEX_ID)));
        when(madeDexRecordRepository.save(any(MadeDexRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("한국 기준 오늘이면 서버가 UTC로 어제여도 기록할 수 있다")
    void 서울_기준으로_오늘을_판정한다() {
        service.create(AUTHOR_ID, MADE_DEX_ID, request(TODAY_SEOUL, List.of("key1"), List.of("김치찌개")));

        verify(madeDexRecordRepository).save(any(MadeDexRecord.class));
    }

    @Test
    @DisplayName("아직 오지 않은 날은 기록할 수 없다")
    void 미래_날짜는_거절한다() {
        assertThatThrownBy(() -> service.create(AUTHOR_ID, MADE_DEX_ID,
                request(TODAY_SEOUL.plusDays(1), List.of("key1"), List.of("김치찌개"))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_RECORD_FUTURE_DATE);
        verify(madeDexRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("지난 날짜는 기록할 수 있다")
    void 지난_날짜는_허용한다() {
        service.create(AUTHOR_ID, MADE_DEX_ID,
                request(TODAY_SEOUL.minusDays(3), List.of("key1"), List.of("김치찌개")));

        verify(madeDexRecordRepository).save(any(MadeDexRecord.class));
    }

    @Test
    @DisplayName("사진이 없으면 거절한다")
    void 사진이_없으면_거절한다() {
        assertThatThrownBy(() -> service.create(AUTHOR_ID, MADE_DEX_ID,
                request(TODAY_SEOUL, List.of(), List.of("김치찌개"))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_RECORD_PHOTO_REQUIRED);
    }

    @Test
    @DisplayName("사진이 8장을 넘으면 거절한다")
    void 사진_상한을_넘으면_거절한다() {
        List<String> nine = IntStream.range(0, 9).mapToObj(i -> "key" + i).toList();

        assertThatThrownBy(() -> service.create(AUTHOR_ID, MADE_DEX_ID,
                request(TODAY_SEOUL, nine, List.of("김치찌개"))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_RECORD_PHOTO_TOO_MANY);
    }

    @Test
    @DisplayName("사진 8장은 저장한다")
    void 사진_여덟장은_저장한다() {
        List<String> eight = IntStream.range(0, 8).mapToObj(i -> "key" + i).toList();

        service.create(AUTHOR_ID, MADE_DEX_ID, request(TODAY_SEOUL, eight, List.of("김치찌개")));

        verify(madeDexRecordRepository).save(any(MadeDexRecord.class));
    }

    @Test
    @DisplayName("같은 사진을 두 번 보내면 한 장으로 접는다")
    void 중복_사진은_한_장으로_접는다() {
        service.create(AUTHOR_ID, MADE_DEX_ID,
                request(TODAY_SEOUL, List.of("key1", "key1", "key2"), List.of("김치찌개")));

        ArgumentCaptor<List<MadeDexRecordPhoto>> captor = ArgumentCaptor.forClass(List.class);
        verify(madeDexRecordPhotoRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(MadeDexRecordPhoto::getImageKey)
                .containsExactly("key1", "key2");
    }

    @Test
    @DisplayName("사진 key가 512자를 넘으면 거절한다 — DB 예외로 500이 나가지 않게 한다")
    void 너무_긴_사진_key는_거절한다() {
        String tooLong = "k".repeat(MadeDexRecordPhoto.IMAGE_KEY_MAX + 1);

        assertThatThrownBy(() -> service.create(AUTHOR_ID, MADE_DEX_ID,
                request(TODAY_SEOUL, List.of(tooLong), List.of("김치찌개"))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_IMAGE_KEY_TOO_LONG);
    }

    @Test
    @DisplayName("장소 이름이 255자를 넘으면 거절한다")
    void 너무_긴_장소_이름은_거절한다() {
        String tooLong = "가".repeat(MadeDexRecord.LOCATION_NAME_MAX + 1);
        MadeDexRecordCreateRequestDTO request = new MadeDexRecordCreateRequestDTO(
                SLOT_ID, TODAY_SEOUL, List.of("key1"), List.of("김치찌개"), null, tooLong, null, null);

        assertThatThrownBy(() -> service.create(AUTHOR_ID, MADE_DEX_ID, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_RECORD_LOCATION_TOO_LONG);
    }

    @Test
    @DisplayName("음식 이름이 없으면 거절한다")
    void 음식명이_없으면_거절한다() {
        assertThatThrownBy(() -> service.create(AUTHOR_ID, MADE_DEX_ID,
                request(TODAY_SEOUL, List.of("key1"), List.of("  "))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_RECORD_FOOD_REQUIRED);
    }

    @Test
    @DisplayName("멤버가 아니면 기록할 수 없다")
    void 비멤버는_기록할_수_없다() {
        assertThatThrownBy(() -> service.create(STRANGER_ID, MADE_DEX_ID,
                request(TODAY_SEOUL, List.of("key1"), List.of("김치찌개"))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_NOT_MEMBER);
    }

    @Test
    @DisplayName("다른 그룹의 슬롯에는 기록할 수 없다")
    void 남의_슬롯에는_기록할_수_없다() {
        when(madeDexSlotRepository.findById(SLOT_ID)).thenReturn(Optional.of(slot(SLOT_ID, OTHER_DEX_ID)));

        assertThatThrownBy(() -> service.create(AUTHOR_ID, MADE_DEX_ID,
                request(TODAY_SEOUL, List.of("key1"), List.of("김치찌개"))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_SLOT_NOT_FOUND);
    }

    @Test
    @DisplayName("숨긴 슬롯에는 새로 기록할 수 없다")
    void 숨긴_슬롯에는_기록할_수_없다() {
        MadeDexSlot hidden = slot(SLOT_ID, MADE_DEX_ID);
        hidden.hide(NOW);
        when(madeDexSlotRepository.findById(SLOT_ID)).thenReturn(Optional.of(hidden));

        assertThatThrownBy(() -> service.create(AUTHOR_ID, MADE_DEX_ID,
                request(TODAY_SEOUL, List.of("key1"), List.of("김치찌개"))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_SLOT_HIDDEN);
    }

    @Test
    @DisplayName("남의 기록은 고칠 수 없다 — 그룹장도 마찬가지다")
    void 작성자만_수정한다() {
        when(madeDexRecordRepository.findByIdAndDeletedAtIsNull(RECORD_ID))
                .thenReturn(Optional.of(record(AUTHOR_ID, MADE_DEX_ID)));

        assertThatThrownBy(() -> service.update(OTHER_MEMBER_ID, MADE_DEX_ID, RECORD_ID,
                updateRequest(TODAY_SEOUL, List.of("key1"), List.of("김치찌개"))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_RECORD_NOT_AUTHOR);
    }

    @Test
    @DisplayName("남의 기록은 지울 수 없다")
    void 작성자만_삭제한다() {
        when(madeDexRecordRepository.findByIdAndDeletedAtIsNull(RECORD_ID))
                .thenReturn(Optional.of(record(AUTHOR_ID, MADE_DEX_ID)));

        assertThatThrownBy(() -> service.delete(OTHER_MEMBER_ID, MADE_DEX_ID, RECORD_ID))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_RECORD_NOT_AUTHOR);
    }

    @Test
    @DisplayName("삭제는 소프트 삭제다 — 슬롯 삭제 판정이 지운 기록까지 세고 있다")
    void 삭제는_소프트_삭제다() {
        MadeDexRecord record = record(AUTHOR_ID, MADE_DEX_ID);
        when(madeDexRecordRepository.findByIdAndDeletedAtIsNull(RECORD_ID)).thenReturn(Optional.of(record));
        when(madeDexRecordPhotoRepository.findByRecordIdOrderBySortOrderAsc(RECORD_ID))
                .thenReturn(List.of(MadeDexRecordPhoto.of(RECORD_ID, "key1", 0)));

        service.delete(AUTHOR_ID, MADE_DEX_ID, RECORD_ID);

        assertThat(record.isDeleted()).isTrue();
        verify(madeDexRecordRepository, never()).delete(any());
        // 아무도 참조하지 않는 S3 객체는 커밋 이후 정리한다
        verify(eventPublisher).publishEvent(any(S3ObjectUnusedEvent.class));
    }

    @Test
    @DisplayName("다른 그룹의 기록 id는 찾을 수 없다고 답한다")
    void 남의_기록은_보이지_않는다() {
        when(madeDexRecordRepository.findByIdAndDeletedAtIsNull(RECORD_ID))
                .thenReturn(Optional.of(record(AUTHOR_ID, OTHER_DEX_ID)));

        assertThatThrownBy(() -> service.delete(AUTHOR_ID, MADE_DEX_ID, RECORD_ID))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_RECORD_NOT_FOUND);
    }

    @Test
    @DisplayName("수정에서 빠진 사진만 정리 대상으로 알린다")
    void 교체된_사진만_정리한다() {
        MadeDexRecord record = record(AUTHOR_ID, MADE_DEX_ID);
        when(madeDexRecordRepository.findByIdAndDeletedAtIsNull(RECORD_ID)).thenReturn(Optional.of(record));
        when(madeDexRecordPhotoRepository.findByRecordIdOrderBySortOrderAsc(RECORD_ID))
                .thenReturn(List.of(
                        MadeDexRecordPhoto.of(RECORD_ID, "keep", 0),
                        MadeDexRecordPhoto.of(RECORD_ID, "drop", 1)));

        service.update(AUTHOR_ID, MADE_DEX_ID, RECORD_ID,
                updateRequest(TODAY_SEOUL, List.of("keep"), List.of("김치찌개")));

        verify(eventPublisher).publishEvent(new S3ObjectUnusedEvent("drop"));
        verify(eventPublisher, never()).publishEvent(new S3ObjectUnusedEvent("keep"));
    }

    private MadeDexRecordCreateRequestDTO request(LocalDate loggedOn, List<String> keys, List<String> foods) {
        return new MadeDexRecordCreateRequestDTO(SLOT_ID, loggedOn, keys, foods, null, null, null, null);
    }

    private MadeDexRecordUpdateRequestDTO updateRequest(LocalDate loggedOn, List<String> keys, List<String> foods) {
        return new MadeDexRecordUpdateRequestDTO(SLOT_ID, loggedOn, keys, foods, null, null, null, null);
    }

    private MadeDex madeDex() {
        MadeDex madeDex = MadeDex.open(AUTHOR_ID, "우리 식탁", null, Visibility.PRIVATE, null);
        ReflectionTestUtils.setField(madeDex, "id", MADE_DEX_ID);
        return madeDex;
    }

    private MadeDexSlot slot(Long id, Long madeDexId) {
        MadeDexSlot slot = MadeDexSlot.of(madeDexId, "아침", 0);
        ReflectionTestUtils.setField(slot, "id", id);
        return slot;
    }

    private MadeDexRecord record(Long authorId, Long madeDexId) {
        MadeDexRecord record = MadeDexRecord.write(
                madeDexId, SLOT_ID, authorId, TODAY_SEOUL, null, null, null, null);
        ReflectionTestUtils.setField(record, "id", RECORD_ID);
        return record;
    }
}
