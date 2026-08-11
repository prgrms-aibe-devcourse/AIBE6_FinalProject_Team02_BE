package com.backend_catcheat.domain.made.service;

import com.backend_catcheat.domain.auth.repository.UserRepository;
import com.backend_catcheat.domain.made.dto.MadeDexRecordCreateRequestDTO;
import com.backend_catcheat.domain.made.dto.MadeDexRecordCreateRequestDTO.PhotoInput;
import com.backend_catcheat.domain.made.dto.MadeDexRecordUpdateRequestDTO.KeptPhoto;
import com.backend_catcheat.domain.made.dto.MadeDexRecordUpdateRequestDTO;
import com.backend_catcheat.domain.made.entity.MadeDex;
import com.backend_catcheat.domain.made.entity.MadeDexRecord;
import com.backend_catcheat.domain.made.entity.MadeDexRecordPhoto;
import com.backend_catcheat.domain.made.entity.MadeDexSlot;
import com.backend_catcheat.domain.made.repository.MadeDexMemberRepository;
import com.backend_catcheat.domain.made.repository.MadeDexRecordPhotoRepository;
import com.backend_catcheat.domain.made.repository.MadeDexRecordRepository;
import com.backend_catcheat.domain.made.repository.MadeDexSlotRepository;
import com.backend_catcheat.global.event.S3ObjectUnusedEvent;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import com.backend_catcheat.domain.upload.dto.UploadPurpose;
import com.backend_catcheat.domain.upload.service.UploadObjectService;
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
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
    private static final long OTHER_SLOT_ID = 21L;
    private static final long RECORD_ID = 30L;

    private static final ZoneId ZONE = ZoneId.systemDefault();
    // 한국 시각으로는 8월 7일 오전이지만 UTC로는 아직 8월 6일인 순간
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 6, 23, 30);
    private static final LocalDate TODAY_SEOUL = LocalDate.of(2026, 8, 7);
    private static final LocalDateTime NOW_SEOUL = LocalDateTime.of(2026, 8, 7, 8, 30);

    private final Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC"));

    @Mock MadeDexRecordRepository madeDexRecordRepository;
    @Mock MadeDexRecordPhotoRepository madeDexRecordPhotoRepository;
    @Mock MadeDexSlotRepository madeDexSlotRepository;
    @Mock MadeDexMemberRepository madeDexMemberRepository;
    @Mock MadeDexFinder madeDexFinder;
    @Mock UserRepository userRepository;
    @Mock S3PresignedUrlService s3PresignedUrlService;
    @Mock UploadObjectService uploadObjectService;
    @Mock ApplicationEventPublisher eventPublisher;

    MadeDexRecordService service;

    @BeforeEach
    void setUp() {
        service = new MadeDexRecordService(
                madeDexRecordRepository, madeDexRecordPhotoRepository,
                madeDexSlotRepository, madeDexMemberRepository, madeDexFinder,
                userRepository, s3PresignedUrlService, uploadObjectService, eventPublisher, clock);

        when(madeDexFinder.active(MADE_DEX_ID)).thenReturn(madeDex());
        when(madeDexMemberRepository.existsByMadeDexIdAndUserId(MADE_DEX_ID, AUTHOR_ID)).thenReturn(true);
        when(madeDexMemberRepository.existsByMadeDexIdAndUserId(MADE_DEX_ID, OTHER_MEMBER_ID)).thenReturn(true);
        when(madeDexMemberRepository.existsByMadeDexIdAndUserId(MADE_DEX_ID, STRANGER_ID)).thenReturn(false);
        when(madeDexSlotRepository.findById(SLOT_ID)).thenReturn(Optional.of(slot(SLOT_ID, MADE_DEX_ID)));
        when(madeDexRecordRepository.saveAndFlush(any(MadeDexRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("한국 기준 오늘이면 서버가 UTC로 어제여도 기록할 수 있다")
    void 서울_기준으로_오늘을_판정한다() {
        service.create(AUTHOR_ID, MADE_DEX_ID, request(TODAY_SEOUL, List.of("key1")));

        verify(madeDexRecordRepository).saveAndFlush(any(MadeDexRecord.class));
    }

    @Test
    @DisplayName("시각을 적으면 기록한 날짜에 붙여 남긴다")
    void 적어_준_시각을_남긴다() {
        service.create(AUTHOR_ID, MADE_DEX_ID, new MadeDexRecordCreateRequestDTO(
                SLOT_ID, TODAY_SEOUL, LocalTime.of(16, 0),
                List.of(photoInput("key1", null))));

        ArgumentCaptor<MadeDexRecord> captor = ArgumentCaptor.forClass(MadeDexRecord.class);
        verify(madeDexRecordRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getLoggedAt()).isEqualTo(TODAY_SEOUL.atTime(16, 0));
    }

    @Test
    @DisplayName("시각을 안 적으면 비워 둔다 — 화면도 시각을 띄우지 않는다")
    void 시각을_안_적으면_비운다() {
        service.create(AUTHOR_ID, MADE_DEX_ID, request(TODAY_SEOUL, List.of("key1")));

        ArgumentCaptor<MadeDexRecord> captor = ArgumentCaptor.forClass(MadeDexRecord.class);
        verify(madeDexRecordRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getLoggedAt()).isNull();
    }

    @Test
    @DisplayName("사진 key를 쓸 자격이 있는지 발급 기록으로 확인한다")
    void 사진_key의_주인을_확인한다() {
        service.create(AUTHOR_ID, MADE_DEX_ID, request(TODAY_SEOUL, List.of("key1")));

        verify(uploadObjectService).requireUsableBy(AUTHOR_ID, List.of("key1"), UploadPurpose.LOGIT_RECORD);
    }

    @Test
    @DisplayName("글은 사진마다 붙는다")
    void 캡션을_사진마다_저장한다() {
        service.create(AUTHOR_ID, MADE_DEX_ID, requestWithPhotos(TODAY_SEOUL,
                List.of(photoInput("key1", "츠르릅"), photoInput("key2", null))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MadeDexRecordPhoto>> captor = ArgumentCaptor.forClass(List.class);
        verify(madeDexRecordPhotoRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(MadeDexRecordPhoto::getCaption)
                .containsExactly("츠르릅", null);
    }

    @Test
    @DisplayName("사진에 붙이는 글이 100자를 넘으면 거절한다")
    void 너무_긴_캡션은_거절한다() {
        String tooLong = "가".repeat(MadeDexRecordPhoto.CAPTION_MAX + 1);

        assertThatThrownBy(() -> service.create(AUTHOR_ID, MADE_DEX_ID, requestWithPhotos(TODAY_SEOUL,
                List.of(photoInput("key1", tooLong)))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_RECORD_CAPTION_TOO_LONG);
    }

    @Test
    @DisplayName("아직 오지 않은 날은 기록할 수 없다")
    void 미래_날짜는_거절한다() {
        assertThatThrownBy(() -> service.create(AUTHOR_ID, MADE_DEX_ID,
                request(TODAY_SEOUL.plusDays(1), List.of("key1"))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_RECORD_FUTURE_DATE);
        verify(madeDexRecordRepository, never()).saveAndFlush(any(MadeDexRecord.class));
    }

    @Test
    @DisplayName("지난 날은 기록할 수 없다 — 열람만 된다")
    void 지난_날짜는_거절한다() {
        assertThatThrownBy(() -> service.create(AUTHOR_ID, MADE_DEX_ID,
                request(TODAY_SEOUL.minusDays(3), List.of("key1"))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_RECORD_PAST_DATE);

        verify(madeDexRecordRepository, never()).saveAndFlush(any(MadeDexRecord.class));
    }

    @Test
    @DisplayName("사진이 없으면 거절한다")
    void 사진이_없으면_거절한다() {
        assertThatThrownBy(() -> service.create(AUTHOR_ID, MADE_DEX_ID,
                request(TODAY_SEOUL, List.of())))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_RECORD_PHOTO_REQUIRED);
    }

    @Test
    @DisplayName("사진이 8장을 넘으면 거절한다")
    void 사진_상한을_넘으면_거절한다() {
        List<String> nine = IntStream.range(0, 9).mapToObj(i -> "key" + i).toList();

        assertThatThrownBy(() -> service.create(AUTHOR_ID, MADE_DEX_ID,
                request(TODAY_SEOUL, nine)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_RECORD_PHOTO_TOO_MANY);
    }

    @Test
    @DisplayName("사진 8장은 저장한다")
    void 사진_여덟장은_저장한다() {
        List<String> eight = IntStream.range(0, 8).mapToObj(i -> "key" + i).toList();

        service.create(AUTHOR_ID, MADE_DEX_ID, request(TODAY_SEOUL, eight));

        verify(madeDexRecordRepository).saveAndFlush(any(MadeDexRecord.class));
    }

    @Test
    @DisplayName("같은 사진을 두 번 보내면 한 장으로 접는다")
    void 중복_사진은_한_장으로_접는다() {
        service.create(AUTHOR_ID, MADE_DEX_ID,
                request(TODAY_SEOUL, List.of("key1", "key1", "key2")));

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
                request(TODAY_SEOUL, List.of(tooLong))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_IMAGE_KEY_TOO_LONG);
    }

    @Test
    @DisplayName("멤버가 아니면 기록할 수 없다")
    void 비멤버는_기록할_수_없다() {
        assertThatThrownBy(() -> service.create(STRANGER_ID, MADE_DEX_ID,
                request(TODAY_SEOUL, List.of("key1"))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_NOT_MEMBER);
    }

    @Test
    @DisplayName("다른 그룹의 슬롯에는 기록할 수 없다")
    void 남의_슬롯에는_기록할_수_없다() {
        when(madeDexSlotRepository.findById(SLOT_ID)).thenReturn(Optional.of(slot(SLOT_ID, OTHER_DEX_ID)));

        assertThatThrownBy(() -> service.create(AUTHOR_ID, MADE_DEX_ID,
                request(TODAY_SEOUL, List.of("key1"))))
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
                request(TODAY_SEOUL, List.of("key1"))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_SLOT_HIDDEN);
    }

    @Test
    @DisplayName("남의 기록은 고칠 수 없다 — 그룹장도 마찬가지다")
    void 작성자만_수정한다() {
        when(madeDexRecordRepository.findActiveByIdForUpdate(RECORD_ID))
                .thenReturn(Optional.of(record(AUTHOR_ID, MADE_DEX_ID)));

        assertThatThrownBy(() -> service.update(OTHER_MEMBER_ID, MADE_DEX_ID, RECORD_ID,
                updateRequest(List.of(), List.of("key1"))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_RECORD_NOT_AUTHOR);
    }

    @Test
    @DisplayName("남의 기록은 지울 수 없다")
    void 작성자만_삭제한다() {
        when(madeDexRecordRepository.findActiveByIdForUpdate(RECORD_ID))
                .thenReturn(Optional.of(record(AUTHOR_ID, MADE_DEX_ID)));

        assertThatThrownBy(() -> service.delete(OTHER_MEMBER_ID, MADE_DEX_ID, RECORD_ID))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_RECORD_NOT_AUTHOR);
    }

    @Test
    @DisplayName("삭제는 소프트 삭제다 — 슬롯 삭제 판정이 지운 기록까지 세고 있다")
    void 삭제는_소프트_삭제다() {
        MadeDexRecord record = record(AUTHOR_ID, MADE_DEX_ID);
        when(madeDexRecordRepository.findActiveByIdForUpdate(RECORD_ID)).thenReturn(Optional.of(record));
        when(madeDexRecordPhotoRepository.findByRecordIdOrderBySortOrderAsc(RECORD_ID))
                .thenReturn(List.of(MadeDexRecordPhoto.of(RECORD_ID, "key1", null, 0)));

        service.delete(AUTHOR_ID, MADE_DEX_ID, RECORD_ID);

        assertThat(record.isDeleted()).isTrue();
        verify(madeDexRecordRepository, never()).delete(any());
        // 아무도 참조하지 않는 S3 객체는 커밋 이후 정리한다
        verify(eventPublisher).publishEvent(any(S3ObjectUnusedEvent.class));
    }

    @Test
    @DisplayName("다른 그룹의 기록 id는 찾을 수 없다고 답한다")
    void 남의_기록은_보이지_않는다() {
        when(madeDexRecordRepository.findActiveByIdForUpdate(RECORD_ID))
                .thenReturn(Optional.of(record(AUTHOR_ID, OTHER_DEX_ID)));

        assertThatThrownBy(() -> service.delete(AUTHOR_ID, MADE_DEX_ID, RECORD_ID))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_RECORD_NOT_FOUND);
    }

    @Test
    @DisplayName("수정에서 빠진 사진만 정리 대상으로 알린다")
    void 교체된_사진만_정리한다() {
        MadeDexRecord record = record(AUTHOR_ID, MADE_DEX_ID);
        when(madeDexRecordRepository.findActiveByIdForUpdate(RECORD_ID)).thenReturn(Optional.of(record));
        when(madeDexRecordPhotoRepository.findByRecordIdOrderBySortOrderAsc(RECORD_ID))
                .thenReturn(List.of(photo(1L, "keep", 0), photo(2L, "drop", 1)));

        service.update(AUTHOR_ID, MADE_DEX_ID, RECORD_ID,
                updateRequest(List.of(1L), List.of()));

        verify(eventPublisher).publishEvent(new S3ObjectUnusedEvent("drop"));
        verify(eventPublisher, never()).publishEvent(new S3ObjectUnusedEvent("keep"));
    }

    @Test
    @DisplayName("사진은 그대로 두고 글만 고칠 수 있다")
    void 남겨_둔_사진의_글을_고친다() {
        MadeDexRecord record = record(AUTHOR_ID, MADE_DEX_ID);
        MadeDexRecordPhoto kept = photo(1L, "keep", 0);
        when(madeDexRecordRepository.findActiveByIdForUpdate(RECORD_ID)).thenReturn(Optional.of(record));
        when(madeDexRecordPhotoRepository.findByRecordIdOrderBySortOrderAsc(RECORD_ID))
                .thenReturn(List.of(kept));

        service.update(AUTHOR_ID, MADE_DEX_ID, RECORD_ID, new MadeDexRecordUpdateRequestDTO(
                SLOT_ID, null, List.of(keptPhoto(1L, "고친 글")), List.of()));

        assertThat(kept.getCaption()).isEqualTo("고친 글");
    }

    @Test
    @DisplayName("남의 기록에 붙은 사진 id는 유지 목록에 넣을 수 없다")
    void 이_기록의_사진이_아니면_거절한다() {
        MadeDexRecord record = record(AUTHOR_ID, MADE_DEX_ID);
        when(madeDexRecordRepository.findActiveByIdForUpdate(RECORD_ID)).thenReturn(Optional.of(record));
        when(madeDexRecordPhotoRepository.findByRecordIdOrderBySortOrderAsc(RECORD_ID))
                .thenReturn(List.of(photo(1L, "mine", 0)));

        assertThatThrownBy(() -> service.update(AUTHOR_ID, MADE_DEX_ID, RECORD_ID,
                updateRequest(List.of(99L), List.of())))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_RECORD_PHOTO_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 살아 있는 기록이 같은 사진을 쓰면 지우지 않는다")
    void 다른_기록이_쓰는_사진은_남긴다() {
        MadeDexRecord record = record(AUTHOR_ID, MADE_DEX_ID);
        when(madeDexRecordRepository.findActiveByIdForUpdate(RECORD_ID)).thenReturn(Optional.of(record));
        when(madeDexRecordPhotoRepository.findByRecordIdOrderBySortOrderAsc(RECORD_ID))
                .thenReturn(List.of(photo(1L, "shared", 0)));
        when(madeDexRecordPhotoRepository.existsInOtherActiveRecord("shared", RECORD_ID)).thenReturn(true);

        service.delete(AUTHOR_ID, MADE_DEX_ID, RECORD_ID);

        assertThat(record.isDeleted()).isTrue();
        verify(eventPublisher, never()).publishEvent(any(S3ObjectUnusedEvent.class));
    }

    // ── 하루 한 끼니 한 건 ────────────────────────────────

    @Test
    @DisplayName("이미 기록한 끼니에 또 올리면 거절한다")
    void 같은_끼니는_두_번_못_쓴다() {
        when(madeDexRecordRepository.existsByMadeDexIdAndSlotIdAndAuthorIdAndLoggedOnAndDeletedAtIsNull(
                MADE_DEX_ID, SLOT_ID, AUTHOR_ID, TODAY_SEOUL)).thenReturn(true);

        assertThatThrownBy(() -> service.create(AUTHOR_ID, MADE_DEX_ID,
                request(TODAY_SEOUL, List.of("key1"))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_RECORD_SLOT_TAKEN);

        verify(madeDexRecordRepository, never()).saveAndFlush(any(MadeDexRecord.class));
    }

    @Test
    @DisplayName("선검사를 함께 통과한 동시 요청은 DB 제약이 막고, 같은 답으로 바뀐다")
    void 동시_등록은_제약이_막는다() {
        when(madeDexRecordRepository.saveAndFlush(any(MadeDexRecord.class))).thenThrow(slotCollision());

        assertThatThrownBy(() -> service.create(AUTHOR_ID, MADE_DEX_ID,
                request(TODAY_SEOUL, List.of("key1"))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_RECORD_SLOT_TAKEN);
    }

    @Test
    @DisplayName("끼니를 옮길 때 그쪽이 이미 차 있으면 거절한다")
    void 옮길_끼니가_차_있으면_거절한다() {
        when(madeDexRecordRepository.findActiveByIdForUpdate(RECORD_ID))
                .thenReturn(Optional.of(record(AUTHOR_ID, MADE_DEX_ID)));
        when(madeDexSlotRepository.findById(OTHER_SLOT_ID))
                .thenReturn(Optional.of(slot(OTHER_SLOT_ID, MADE_DEX_ID)));
        when(madeDexRecordPhotoRepository.findByRecordIdOrderBySortOrderAsc(RECORD_ID))
                .thenReturn(List.of(photo(1L, "keep", 0)));
        when(madeDexRecordRepository.existsByMadeDexIdAndSlotIdAndAuthorIdAndLoggedOnAndDeletedAtIsNullAndIdNot(
                MADE_DEX_ID, OTHER_SLOT_ID, AUTHOR_ID, TODAY_SEOUL, RECORD_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.update(AUTHOR_ID, MADE_DEX_ID, RECORD_ID,
                new MadeDexRecordUpdateRequestDTO(
                        OTHER_SLOT_ID, null, List.of(keptPhoto(1L, null)), List.of())))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_RECORD_SLOT_TAKEN);
    }

    // ── 지난 기록은 글만 ──────────────────────────────────

    @Test
    @DisplayName("지난 기록도 사진에 붙인 글은 고칠 수 있다")
    void 지난_기록의_글은_고친다() {
        MadeDexRecordPhoto kept = photo(1L, "keep", 0);
        givenPastRecord(kept);

        service.update(AUTHOR_ID, MADE_DEX_ID, RECORD_ID, new MadeDexRecordUpdateRequestDTO(
                SLOT_ID, null, List.of(keptPhoto(1L, "어제의 한마디")), List.of()));

        assertThat(kept.getCaption()).isEqualTo("어제의 한마디");
    }

    @Test
    @DisplayName("지난 기록에 사진을 더하면 거절한다")
    void 지난_기록에_사진을_못_더한다() {
        givenPastRecord(photo(1L, "keep", 0));

        assertThatThrownBy(() -> service.update(AUTHOR_ID, MADE_DEX_ID, RECORD_ID,
                new MadeDexRecordUpdateRequestDTO(
                        SLOT_ID, null, List.of(keptPhoto(1L, null)), List.of(photoInput("new", null)))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_RECORD_PAST_LOCKED);
    }

    @Test
    @DisplayName("지난 기록은 사진을 빼거나 순서를 바꿀 수 없다")
    void 지난_기록의_사진_구성은_잠긴다() {
        givenPastRecord(photo(1L, "first", 0), photo(2L, "second", 1));

        // 순서 뒤집기
        assertThatThrownBy(() -> service.update(AUTHOR_ID, MADE_DEX_ID, RECORD_ID,
                new MadeDexRecordUpdateRequestDTO(
                        SLOT_ID, null, List.of(keptPhoto(2L, null), keptPhoto(1L, null)), List.of())))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_RECORD_PAST_LOCKED);

        // 한 장 빼기
        assertThatThrownBy(() -> service.update(AUTHOR_ID, MADE_DEX_ID, RECORD_ID,
                new MadeDexRecordUpdateRequestDTO(
                        SLOT_ID, null, List.of(keptPhoto(1L, null)), List.of())))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_RECORD_PAST_LOCKED);
    }

    @Test
    @DisplayName("지난 기록은 사진 위치 조정도 반영하지 않는다")
    void 지난_기록의_crop은_잠긴다() {
        MadeDexRecordPhoto kept = photo(1L, "keep", 0);
        givenPastRecord(kept);

        service.update(AUTHOR_ID, MADE_DEX_ID, RECORD_ID, new MadeDexRecordUpdateRequestDTO(
                SLOT_ID, null,
                List.of(new KeptPhoto(1L, "글만 바뀐다", 10.0, 90.0)), List.of()));

        assertThat(kept.getCaption()).isEqualTo("글만 바뀐다");
        assertThat(kept.getCropX()).isEqualTo(50);
        assertThat(kept.getCropY()).isEqualTo(50);
    }

    @Test
    @DisplayName("지난 기록은 끼니를 옮길 수 없다")
    void 지난_기록의_끼니는_잠긴다() {
        givenPastRecord(photo(1L, "keep", 0));
        when(madeDexSlotRepository.findById(OTHER_SLOT_ID))
                .thenReturn(Optional.of(slot(OTHER_SLOT_ID, MADE_DEX_ID)));

        assertThatThrownBy(() -> service.update(AUTHOR_ID, MADE_DEX_ID, RECORD_ID,
                new MadeDexRecordUpdateRequestDTO(
                        OTHER_SLOT_ID, null, List.of(keptPhoto(1L, null)), List.of())))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_RECORD_PAST_LOCKED);
    }

    @Test
    @DisplayName("지난 기록도 지울 수 있다")
    void 지난_기록은_지울_수_있다() {
        MadeDexRecord past = pastRecord();
        when(madeDexRecordRepository.findActiveByIdForUpdate(RECORD_ID)).thenReturn(Optional.of(past));
        when(madeDexRecordPhotoRepository.findByRecordIdOrderBySortOrderAsc(RECORD_ID))
                .thenReturn(List.of(photo(1L, "keep", 0)));

        service.delete(AUTHOR_ID, MADE_DEX_ID, RECORD_ID);

        assertThat(past.isDeleted()).isTrue();
    }

    /** 어제 남긴 기록. 사진은 인자로 준 것이 그대로 붙어 있다 */
    private void givenPastRecord(MadeDexRecordPhoto... photos) {
        when(madeDexRecordRepository.findActiveByIdForUpdate(RECORD_ID))
                .thenReturn(Optional.of(pastRecord()));
        when(madeDexRecordPhotoRepository.findByRecordIdOrderBySortOrderAsc(RECORD_ID))
                .thenReturn(List.of(photos));
    }

    private MadeDexRecord pastRecord() {
        MadeDexRecord record = MadeDexRecord.write(
                MADE_DEX_ID, SLOT_ID, AUTHOR_ID, TODAY_SEOUL.minusDays(1), null);
        ReflectionTestUtils.setField(record, "id", RECORD_ID);
        return record;
    }

    // ── 유니크 위반을 어디까지 변환하나 ──────────────────

    @Test
    @DisplayName("끼니를 옮길 때도 동시 요청은 DB 제약이 막고, 같은 답으로 바뀐다")
    void 끼니_이동도_제약이_막는다() {
        when(madeDexRecordRepository.findActiveByIdForUpdate(RECORD_ID))
                .thenReturn(Optional.of(record(AUTHOR_ID, MADE_DEX_ID)));
        when(madeDexSlotRepository.findById(OTHER_SLOT_ID))
                .thenReturn(Optional.of(slot(OTHER_SLOT_ID, MADE_DEX_ID)));
        when(madeDexRecordPhotoRepository.findByRecordIdOrderBySortOrderAsc(RECORD_ID))
                .thenReturn(List.of(photo(1L, "keep", 0)));
        // 선검사는 통과시키고, 앞당긴 flush에서 걸리게 한다
        doThrow(slotCollision()).when(madeDexRecordRepository).flush();

        assertThatThrownBy(() -> service.update(AUTHOR_ID, MADE_DEX_ID, RECORD_ID,
                new MadeDexRecordUpdateRequestDTO(
                        OTHER_SLOT_ID, null, List.of(keptPhoto(1L, null)), List.of())))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_RECORD_SLOT_TAKEN);
    }

    @Test
    @DisplayName("끼니를 그대로 두는 수정은 flush를 앞당기지 않는다")
    void 끼니를_안_옮기면_flush하지_않는다() {
        when(madeDexRecordRepository.findActiveByIdForUpdate(RECORD_ID))
                .thenReturn(Optional.of(record(AUTHOR_ID, MADE_DEX_ID)));
        when(madeDexRecordPhotoRepository.findByRecordIdOrderBySortOrderAsc(RECORD_ID))
                .thenReturn(List.of(photo(1L, "keep", 0)));

        service.update(AUTHOR_ID, MADE_DEX_ID, RECORD_ID,
                updateRequest(List.of(1L), List.of()));

        verify(madeDexRecordRepository, never()).flush();
    }

    @Test
    @DisplayName("다른 제약 위반은 '이미 기록한 끼니'로 가리지 않고 그대로 올린다")
    void 다른_제약_위반은_가리지_않는다() {
        // 검사 직후 슬롯이 지워지면 복합 FK가 걸린다. 이걸 409로 바꾸면 원인이 사라진다
        DataIntegrityViolationException other = new DataIntegrityViolationException(
                "could not execute statement",
                new ConstraintViolationException("fk violation", new SQLException(),
                        "fk_made_dex_record_slot"));
        when(madeDexRecordRepository.saveAndFlush(any(MadeDexRecord.class))).thenThrow(other);

        assertThatThrownBy(() -> service.create(AUTHOR_ID, MADE_DEX_ID,
                request(TODAY_SEOUL, List.of("key1"))))
                .isSameAs(other);
    }

    // ── 시각이 남아 있는 지난 기록 ────────────────────────

    @Test
    @DisplayName("시각이 있는 지난 기록도 사진에 붙인 글은 고칠 수 있다")
    void 시각이_있는_지난_기록의_글을_고친다() {
        MadeDexRecordPhoto kept = photo(1L, "keep", 0);
        MadeDexRecord past = pastRecordAt(LocalTime.of(16, 0));
        when(madeDexRecordRepository.findActiveByIdForUpdate(RECORD_ID)).thenReturn(Optional.of(past));
        when(madeDexRecordPhotoRepository.findByRecordIdOrderBySortOrderAsc(RECORD_ID))
                .thenReturn(List.of(kept));

        service.update(AUTHOR_ID, MADE_DEX_ID, RECORD_ID, new MadeDexRecordUpdateRequestDTO(
                SLOT_ID, LocalTime.of(16, 0), List.of(keptPhoto(1L, "어제 저녁")), List.of()));

        assertThat(kept.getCaption()).isEqualTo("어제 저녁");
        // 시각은 그대로 남는다
        assertThat(past.getLoggedAt()).isEqualTo(TODAY_SEOUL.minusDays(1).atTime(16, 0));
    }

    @Test
    @DisplayName("지난 기록의 시각을 지우려 하면 거절한다 — 시각도 잠긴다")
    void 지난_기록의_시각은_못_지운다() {
        MadeDexRecord past = pastRecordAt(LocalTime.of(16, 0));
        when(madeDexRecordRepository.findActiveByIdForUpdate(RECORD_ID)).thenReturn(Optional.of(past));
        when(madeDexRecordPhotoRepository.findByRecordIdOrderBySortOrderAsc(RECORD_ID))
                .thenReturn(List.of(photo(1L, "keep", 0)));

        assertThatThrownBy(() -> service.update(AUTHOR_ID, MADE_DEX_ID, RECORD_ID,
                new MadeDexRecordUpdateRequestDTO(
                        SLOT_ID, null, List.of(keptPhoto(1L, "글만")), List.of())))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_RECORD_PAST_LOCKED);
    }

    @Test
    @DisplayName("지난 기록의 시각을 다른 값으로 바꾸려 해도 거절한다")
    void 지난_기록의_시각은_못_바꾼다() {
        MadeDexRecord past = pastRecordAt(LocalTime.of(16, 0));
        when(madeDexRecordRepository.findActiveByIdForUpdate(RECORD_ID)).thenReturn(Optional.of(past));
        when(madeDexRecordPhotoRepository.findByRecordIdOrderBySortOrderAsc(RECORD_ID))
                .thenReturn(List.of(photo(1L, "keep", 0)));

        assertThatThrownBy(() -> service.update(AUTHOR_ID, MADE_DEX_ID, RECORD_ID,
                new MadeDexRecordUpdateRequestDTO(
                        SLOT_ID, LocalTime.of(18, 0), List.of(keptPhoto(1L, null)), List.of())))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MADE_DEX_RECORD_PAST_LOCKED);
    }

    /** 어제 그 시각에 남긴 기록 */
    private MadeDexRecord pastRecordAt(LocalTime loggedTime) {
        LocalDate yesterday = TODAY_SEOUL.minusDays(1);
        MadeDexRecord record = MadeDexRecord.write(
                MADE_DEX_ID, SLOT_ID, AUTHOR_ID, yesterday, yesterday.atTime(loggedTime));
        ReflectionTestUtils.setField(record, "id", RECORD_ID);
        return record;
    }

    /** 사람x끼니x날짜 유니크 인덱스에 걸린 모양 */
    private DataIntegrityViolationException slotCollision() {
        return new DataIntegrityViolationException(
                "could not execute statement",
                new ConstraintViolationException("duplicate key", new SQLException(),
                        "uq_made_dex_record_slot_author_day"));
    }

    // ── 크롭 좌표는 엔티티가 좁힌다 ──────────────────────

    @Test
    @DisplayName("범위 밖 크롭 좌표는 0~100으로 좁혀 저장한다")
    void 크롭_좌표를_좁힌다() {
        service.create(AUTHOR_ID, MADE_DEX_ID, requestWithPhotos(TODAY_SEOUL,
                List.of(new PhotoInput("key1", null, -30.0, 420.0))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MadeDexRecordPhoto>> captor = ArgumentCaptor.forClass(List.class);
        verify(madeDexRecordPhotoRepository).saveAll(captor.capture());
        MadeDexRecordPhoto saved = captor.getValue().getFirst();
        assertThat(saved.getCropX()).isEqualTo(0);
        assertThat(saved.getCropY()).isEqualTo(100);
    }

    @Test
    @DisplayName("크롭 좌표를 안 보내면 가운데로 둔다")
    void 크롭을_안_보내면_가운데다() {
        service.create(AUTHOR_ID, MADE_DEX_ID, request(TODAY_SEOUL, List.of("key1")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MadeDexRecordPhoto>> captor = ArgumentCaptor.forClass(List.class);
        verify(madeDexRecordPhotoRepository).saveAll(captor.capture());
        MadeDexRecordPhoto saved = captor.getValue().getFirst();
        assertThat(saved.getCropX()).isEqualTo(MadeDexRecordPhoto.CROP_DEFAULT);
        assertThat(saved.getCropY()).isEqualTo(MadeDexRecordPhoto.CROP_DEFAULT);
    }

    @Test
    @DisplayName("오늘 기록을 고칠 때도 범위 밖 좌표는 좁혀진다")
    void 수정할_때도_크롭을_좁힌다() {
        MadeDexRecordPhoto kept = photo(1L, "keep", 0);
        when(madeDexRecordRepository.findActiveByIdForUpdate(RECORD_ID))
                .thenReturn(Optional.of(record(AUTHOR_ID, MADE_DEX_ID)));
        when(madeDexRecordPhotoRepository.findByRecordIdOrderBySortOrderAsc(RECORD_ID))
                .thenReturn(List.of(kept));

        service.update(AUTHOR_ID, MADE_DEX_ID, RECORD_ID, new MadeDexRecordUpdateRequestDTO(
                SLOT_ID, null, List.of(new KeptPhoto(1L, null, 999.0, -1.0)), List.of()));

        assertThat(kept.getCropX()).isEqualTo(100);
        assertThat(kept.getCropY()).isEqualTo(0);
    }

    private MadeDexRecordCreateRequestDTO request(LocalDate loggedOn, List<String> keys) {
        return requestWithPhotos(loggedOn, keys.stream().map(key -> photoInput(key, null)).toList());
    }

    private MadeDexRecordCreateRequestDTO requestWithPhotos(LocalDate loggedOn, List<PhotoInput> photos) {
        return new MadeDexRecordCreateRequestDTO(SLOT_ID, loggedOn, null, photos);
    }

    private MadeDexRecordUpdateRequestDTO updateRequest(List<Long> keepPhotoIds, List<String> newKeys) {
        return new MadeDexRecordUpdateRequestDTO(
                SLOT_ID, null,
                keepPhotoIds.stream().map(photoId -> keptPhoto(photoId, null)).toList(),
                newKeys.stream().map(key -> photoInput(key, null)).toList());
    }

    private static PhotoInput photoInput(String key, String caption) {
        return new PhotoInput(key, caption, null, null);
    }

    private static KeptPhoto keptPhoto(Long photoId, String caption) {
        return new KeptPhoto(photoId, caption, null, null);
    }

    private MadeDex madeDex() {
        MadeDex madeDex = MadeDex.open(AUTHOR_ID, "우리 식탁", null, null);
        ReflectionTestUtils.setField(madeDex, "id", MADE_DEX_ID);
        return madeDex;
    }

    private MadeDexSlot slot(Long id, Long madeDexId) {
        MadeDexSlot slot = MadeDexSlot.of(madeDexId, "아침", 0);
        ReflectionTestUtils.setField(slot, "id", id);
        return slot;
    }

    private MadeDexRecordPhoto photo(Long id, String imageKey, int sortOrder) {
        MadeDexRecordPhoto photo = MadeDexRecordPhoto.of(RECORD_ID, imageKey, null, sortOrder);
        ReflectionTestUtils.setField(photo, "id", id);
        return photo;
    }

    private MadeDexRecord record(Long authorId, Long madeDexId) {
        MadeDexRecord record = MadeDexRecord.write(
                madeDexId, SLOT_ID, authorId, TODAY_SEOUL, NOW_SEOUL);
        ReflectionTestUtils.setField(record, "id", RECORD_ID);
        return record;
    }
}
