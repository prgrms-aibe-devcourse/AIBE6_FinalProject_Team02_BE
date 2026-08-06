package com.backend_catcheat.domain.registration.service;

import com.backend_catcheat.domain.dex.basicdex.entity.BasicDexEntity;
import com.backend_catcheat.domain.dex.basicdex.repository.BasicDexRepository;
import com.backend_catcheat.domain.dex.basicdex.type.Category;
import com.backend_catcheat.domain.registration.config.VisionProperties;
import com.backend_catcheat.domain.registration.dto.VerificationRequest;
import com.backend_catcheat.domain.registration.dto.VerificationResponse;
import com.backend_catcheat.domain.registration.dto.ai.AiVerificationResult;
import com.backend_catcheat.domain.registration.entity.Registration;
import com.backend_catcheat.domain.registration.repository.RegistrationRepository;
import com.backend_catcheat.domain.registration.repository.VerificationAttemptRepository;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("음식 검증")
class FoodVerificationServiceTest {

    private static final Long USER_ID = 7L;
    private static final String KEY = "uploads/2026/07/28/a.jpg";

    private RegistrationRepository registrationRepository;
    private VerificationAttemptRepository attemptRepository;
    private BasicDexRepository slotRepository;
    private RegistrationPhotoLoader photoLoader;
    private ImagePreprocessor preprocessor;
    private VisionAnalyzer analyzer;

    private FoodVerificationService service;

    @BeforeEach
    void setUp() {
        registrationRepository = mock(RegistrationRepository.class);
        attemptRepository = mock(VerificationAttemptRepository.class);
        slotRepository = mock(BasicDexRepository.class);
        photoLoader = mock(RegistrationPhotoLoader.class);
        preprocessor = mock(ImagePreprocessor.class);
        analyzer = mock(VisionAnalyzer.class);

        service = new FoodVerificationService(
                registrationRepository, attemptRepository, slotRepository,
                photoLoader, preprocessor, analyzer,
                new VisionProperties(5, 1024, 0.8f, 3_500_000L, 5, ""));

        when(registrationRepository.save(any(Registration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(photoLoader.loadForAnalysis(any())).thenReturn(new byte[]{1, 2, 3});
        when(preprocessor.prepare(any(), any()))
                .thenReturn(new PreparedImage(new byte[]{1}, 3, 10, 10, KEY));
    }

    private BasicDexEntity slot(Long id, String name, Category category) {
        BasicDexEntity entity = mock(BasicDexEntity.class);
        when(entity.getId()).thenReturn(id);
        when(entity.getName()).thenReturn(name);
        when(entity.getCategory()).thenReturn(category);
        return entity;
    }

    // 파싱 자체는 별도 테스트에 맡기고, 여기서는 AI가 돌려준 판정만 재현한다
    private void aiReturns(String json) {
        when(analyzer.verify(any(), anyList())).thenAnswer(invocation ->
                new ObjectMapper().readValue(json, AiVerificationResult.class));
    }

    private VerificationRequest request(Long registrationId, Long... slotIds) {
        return new VerificationRequest(registrationId, List.of(KEY), 0, List.of(slotIds));
    }

    @Test
    @DisplayName("전부 일치하면 allMatched")
    void 전부_일치() {
        BasicDexEntity kimchi = slot(1L, "김치찌개", Category.SOUP_STEW);
        when(slotRepository.findAllById(anyList())).thenReturn(List.of(kimchi));
        aiReturns("{\"verdicts\":[{\"name\":\"김치찌개\",\"matched\":true,\"confidence\":0.95,\"reason\":\"\"}]}");

        VerificationResponse response = service.verify(USER_ID, request(null, 1L));

        assertThat(response.allMatched()).isTrue();
        assertThat(response.retriesLeft()).isEqualTo(2);
        assertThat(response.verdicts()).singleElement().satisfies(v -> {
            assertThat(v.slotName()).isEqualTo("김치찌개");
            assertThat(v.category()).isEqualTo("국·탕·찌개");
            assertThat(v.matched()).isTrue();
            assertThat(v.reason()).isEmpty();
        });
    }

    @Test
    @DisplayName("일부 불일치면 사유가 함께 온다 — 통과한 것만 해금 대상이다")
    void 일부_불일치() {
        BasicDexEntity kimchi = slot(1L, "김치찌개", Category.SOUP_STEW);
        BasicDexEntity pork = slot(2L, "삼겹살", Category.MEAT_DISH);
        when(slotRepository.findAllById(anyList())).thenReturn(List.of(kimchi, pork));
        aiReturns("""
                {"verdicts":[
                  {"name":"김치찌개","matched":true,"confidence":0.9,"reason":""},
                  {"name":"삼겹살","matched":false,"confidence":0.8,"reason":"사진은 김밥으로 보여요"}
                ]}""");

        VerificationResponse response = service.verify(USER_ID, request(null, 1L, 2L));

        assertThat(response.allMatched()).isFalse();
        assertThat(response.verdicts()).extracting(VerificationResponse.SlotVerdict::matched)
                .containsExactly(true, false);
        assertThat(response.verdicts().get(1).reason()).isEqualTo("사진은 김밥으로 보여요");
    }

    @Test
    @DisplayName("AI가 이름을 빠뜨리면 통과시키지 않는다 — 확인되지 않은 것은 해금하지 않는다")
    void AI가_빠뜨리면_불일치() {
        BasicDexEntity kimchi = slot(1L, "김치찌개", Category.SOUP_STEW);
        BasicDexEntity pork = slot(2L, "삼겹살", Category.MEAT_DISH);
        when(slotRepository.findAllById(anyList())).thenReturn(List.of(kimchi, pork));
        aiReturns("{\"verdicts\":[{\"name\":\"김치찌개\",\"matched\":true,\"confidence\":0.9,\"reason\":\"\"}]}");

        VerificationResponse response = service.verify(USER_ID, request(null, 1L, 2L));

        assertThat(response.verdicts().get(1).matched()).isFalse();
        assertThat(response.verdicts().get(1).confidence()).isZero();
        assertThat(response.verdicts().get(1).reason()).isEqualTo("사진에서 이 음식을 확인하지 못했어요");
    }

    @Test
    @DisplayName("결과 순서는 요청한 칸 순서를 지킨다 — 화면의 칩 순서와 맞아야 한다")
    void 요청_순서를_지킨다() {
        // 조회 결과가 뒤섞여 와도 응답은 요청 순서
        BasicDexEntity pork = slot(2L, "삼겹살", Category.MEAT_DISH);
        BasicDexEntity kimchi = slot(1L, "김치찌개", Category.SOUP_STEW);
        when(slotRepository.findAllById(anyList())).thenReturn(List.of(pork, kimchi));
        aiReturns("""
                {"verdicts":[
                  {"name":"김치찌개","matched":true,"confidence":0.9,"reason":""},
                  {"name":"삼겹살","matched":true,"confidence":0.9,"reason":""}
                ]}""");

        VerificationResponse response = service.verify(USER_ID, request(null, 1L, 2L));

        assertThat(response.verdicts()).extracting(VerificationResponse.SlotVerdict::slotId)
                .containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("재시도 상한을 넘으면 거부한다 — 수동 폴백으로 보낸다 (§5.2)")
    void 재시도_상한_초과() {
        BasicDexEntity kimchi = slot(1L, "김치찌개", Category.SOUP_STEW);
        when(slotRepository.findAllById(anyList())).thenReturn(List.of(kimchi));

        Registration exhausted = Registration.start(USER_ID, KEY);
        exhausted.retryWith(KEY);
        exhausted.retryWith(KEY);
        when(registrationRepository.findById(99L)).thenReturn(Optional.of(exhausted));

        assertThatThrownBy(() -> service.verify(USER_ID, request(99L, 1L)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RETRY_LIMIT_EXCEEDED);

        // 상한을 넘으면 AI를 부르지 않는다. 호출 비용이 그냥 나가면 안 된다
        verify(analyzer, never()).verify(any(), anyList());
    }

    @Test
    @DisplayName("남의 등록 건에는 재시도를 붙일 수 없다 — 바디의 id를 신뢰하지 않는다 (§7)")
    void 남의_등록_건은_거부() {
        BasicDexEntity kimchi = slot(1L, "김치찌개", Category.SOUP_STEW);
        when(slotRepository.findAllById(anyList())).thenReturn(List.of(kimchi));
        when(registrationRepository.findById(99L))
                .thenReturn(Optional.of(Registration.start(1234L, KEY)));

        assertThatThrownBy(() -> service.verify(USER_ID, request(99L, 1L)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REGISTRATION_FORBIDDEN);
    }

    @Test
    @DisplayName("도감에 없는 칸 id는 거부한다")
    void 없는_칸은_거부() {
        when(slotRepository.findAllById(anyList())).thenReturn(List.of());

        assertThatThrownBy(() -> service.verify(USER_ID, request(null, 404L)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEX_SLOT_NOT_FOUND);
    }

    @Test
    @DisplayName("음식을 하나도 안 고르면 거부한다 — 이름 없이 AI에게 맞히게 하지 않는다 (§5.2)")
    void 음식이_없으면_거부() {
        assertThatThrownBy(() -> service.verify(USER_ID,
                new VerificationRequest(null, List.of(KEY), 0, List.of())))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FOOD_NAME_REQUIRED);
    }

    @Test
    @DisplayName("음식 6개는 거부한다 — 한 상 사진도 5개까지 (§5.2)")
    void 음식_6개는_거부() {
        assertThatThrownBy(() -> service.verify(USER_ID,
                new VerificationRequest(null, List.of(KEY), 0, List.of(1L, 2L, 3L, 4L, 5L, 6L))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FOOD_NAME_COUNT_EXCEEDED);
    }

    @Test
    @DisplayName("사진이 없으면 거부한다 — 사진 없는 등록 경로는 없다 (§5.2)")
    void 사진이_없으면_거부() {
        assertThatThrownBy(() -> service.verify(USER_ID,
                new VerificationRequest(null, List.of(), 0, List.of(1L))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PHOTO_REQUIRED);
    }

    @Test
    @DisplayName("분석 사진 위치가 범위를 벗어나면 거부한다")
    void 분석_사진_인덱스가_잘못되면_거부() {
        assertThatThrownBy(() -> service.verify(USER_ID,
                new VerificationRequest(null, List.of(KEY), 3, List.of(1L))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ANALYSIS_PHOTO_INVALID);
    }

    @Test
    @DisplayName("재시도하면 남은 횟수가 줄어든다")
    void 재시도하면_남은_횟수가_준다() {
        Registration existing = Registration.start(USER_ID, KEY);
        when(registrationRepository.findById(99L)).thenReturn(Optional.of(existing));
        BasicDexEntity kimchi = slot(1L, "김치찌개", Category.SOUP_STEW);
        when(slotRepository.findAllById(anyList())).thenReturn(List.of(kimchi));
        aiReturns("{\"verdicts\":[{\"name\":\"김치찌개\",\"matched\":true,\"confidence\":0.9,\"reason\":\"\"}]}");

        VerificationResponse response = service.verify(USER_ID, request(99L, 1L));

        assertThat(response.retriesLeft()).isEqualTo(1);
    }
}
