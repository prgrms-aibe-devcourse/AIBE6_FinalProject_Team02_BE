package com.backend_catcheat.domain.upload.service;

import com.backend_catcheat.domain.upload.dto.UploadPurpose;
import com.backend_catcheat.domain.upload.entity.UploadObject;
import com.backend_catcheat.domain.upload.repository.UploadObjectRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("업로드 객체 소유자 검증")
class UploadObjectServiceTest {

    private static final long ME = 1L;
    private static final long OTHER = 2L;
    private static final String KEY = "uploads/2026/08/07/abc.jpg";

    // 한국 시각 8월 7일 오전이지만 UTC로는 아직 8월 6일인 순간
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 6, 23, 30);

    private final Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC"));

    @Mock UploadObjectRepository uploadObjectRepository;

    UploadObjectService service;

    @BeforeEach
    void setUp() {
        service = new UploadObjectService(uploadObjectRepository, clock);
    }

    @Test
    @DisplayName("발급 기록은 한국 시각으로 남긴다")
    void 발급_기록은_한국_시각으로_남긴다() {
        service.issued(ME, List.of(KEY), UploadPurpose.LOGIT_RECORD);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UploadObject>> captor = ArgumentCaptor.forClass(List.class);
        verify(uploadObjectRepository).saveAll(captor.capture());

        UploadObject saved = captor.getValue().getFirst();
        assertThat(saved.getImageKey()).isEqualTo(KEY);
        assertThat(saved.getUploaderId()).isEqualTo(ME);
        assertThat(saved.getPurpose()).isEqualTo(UploadPurpose.LOGIT_RECORD);
        assertThat(saved.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 8, 7, 8, 30));
    }

    @Test
    @DisplayName("주인을 모르면 기록하지 않는다 — 인증 없이 발급되는 경로가 막히면 안 된다")
    void 주인을_모르면_기록하지_않는다() {
        service.issued(null, List.of(KEY), UploadPurpose.DEFAULT);

        verifyNoInteractions(uploadObjectRepository);
    }

    @Test
    @DisplayName("내가 올린 key는 통과한다")
    void 내가_올린_key는_통과한다() {
        when(uploadObjectRepository.findByImageKeyIn(anyCollection()))
                .thenReturn(List.of(UploadObject.issued(KEY, ME, UploadPurpose.LOGIT_RECORD, NOW)));

        assertThatCode(() -> service.requireUsableBy(ME, List.of(KEY), UploadPurpose.LOGIT_RECORD))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("남이 올린 key는 막는다 — key만 알면 남의 사진을 자기 기록에 붙일 수 있다")
    void 남이_올린_key는_막는다() {
        when(uploadObjectRepository.findByImageKeyIn(anyCollection()))
                .thenReturn(List.of(UploadObject.issued(KEY, OTHER, UploadPurpose.LOGIT_RECORD, NOW)));

        assertThatThrownBy(() -> service.requireUsableBy(ME, List.of(KEY), UploadPurpose.LOGIT_RECORD))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UPLOAD_OBJECT_NOT_OWNED);
    }

    @Test
    @DisplayName("다른 용도로 받은 key는 막는다 — 5장 상한으로 받은 key가 8장 화면에 섞이면 안 된다")
    void 다른_용도로_받은_key는_막는다() {
        when(uploadObjectRepository.findByImageKeyIn(anyCollection()))
                .thenReturn(List.of(UploadObject.issued(KEY, ME, UploadPurpose.DEFAULT, NOW)));

        assertThatThrownBy(() -> service.requireUsableBy(ME, List.of(KEY), UploadPurpose.LOGIT_RECORD))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UPLOAD_OBJECT_PURPOSE_MISMATCH);
    }

    @Test
    @DisplayName("기록이 없는 key는 통과시킨다 — 이 기능 이전에 올린 사진의 수정이 끊기면 안 된다")
    void 기록이_없는_key는_통과시킨다() {
        when(uploadObjectRepository.findByImageKeyIn(anyCollection())).thenReturn(List.of());

        assertThatCode(() -> service.requireUsableBy(ME, List.of(KEY), UploadPurpose.LOGIT_RECORD))
                .doesNotThrowAnyException();
    }
}
