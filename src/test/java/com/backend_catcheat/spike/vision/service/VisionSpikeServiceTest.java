package com.backend_catcheat.spike.vision.service;

import com.backend_catcheat.spike.vision.config.VisionSpikeProperties;
import com.backend_catcheat.spike.vision.dto.VisionAnalysisResponse;
import com.backend_catcheat.spike.vision.dto.VisionAnalysisResponse.FoodCandidate;
import com.backend_catcheat.spike.vision.exception.VisionSpikeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("비전 스파이크 서비스")
class VisionSpikeServiceTest {

    private ChatModel chatModel;
    private VisionSpikeService service;

    @BeforeEach
    void setUp() {
        VisionSpikeProperties properties = new VisionSpikeProperties(5, 1024, 0.8f, 3_500_000L, true);
        chatModel = mock(ChatModel.class);

        service = new VisionSpikeService(
                new ImagePreprocessor(properties),
                new VisionAnalyzer(chatModel, new ObjectMapper(), properties, "dummy-key"),
                new DexMatcher(new DexCatalog(new ObjectMapper())),
                properties
        );
    }

    @Test
    @DisplayName("단일 확신 결과를 도감 칸까지 매핑한다")
    void 단일_확신을_매핑한다() throws IOException {
        givenAiResponse("""
                {"foods":[{"candidates":[{"name":"돼지김치찌개","confidence":0.93}]}]}""");

        VisionAnalysisResponse response = service.analyze(List.of(jpeg()), null);

        assertThat(response.foods()).hasSize(1);
        FoodCandidate candidate = response.foods().getFirst().candidates().getFirst();
        assertThat(candidate.aiName()).isEqualTo("돼지김치찌개");
        assertThat(candidate.slotName()).isEqualTo("김치찌개");
        assertThat(candidate.matchType()).isEqualTo("ALIAS");
    }

    @Test
    @DisplayName("후보를 확신도 내림차순으로 정렬한다 — AI가 순서를 어겨도 서버가 보정")
    void 확신도_내림차순으로_정렬한다() throws IOException {
        givenAiResponse("""
                {"foods":[{"candidates":[
                  {"name":"수제비","confidence":0.20},
                  {"name":"칼국수","confidence":0.55},
                  {"name":"잔치국수","confidence":0.25}
                ]}]}""");

        VisionAnalysisResponse response = service.analyze(List.of(jpeg()), null);

        assertThat(response.foods().getFirst().candidates())
                .extracting(FoodCandidate::aiName)
                .containsExactly("칼국수", "잔치국수", "수제비");
    }

    @Test
    @DisplayName("한 상 사진의 복수 음식을 각각 분리한다")
    void 복수_음식을_분리한다() throws IOException {
        givenAiResponse("""
                {"foods":[
                  {"candidates":[{"name":"김치찌개","confidence":0.9}]},
                  {"candidates":[{"name":"제육볶음","confidence":0.85}]},
                  {"candidates":[{"name":"김밥","confidence":0.8}]}
                ]}""");

        VisionAnalysisResponse response = service.analyze(List.of(jpeg()), null);

        assertThat(response.foods()).hasSize(3);
        assertThat(response.foods())
                .allSatisfy(food -> assertThat(food.candidates().getFirst().slotId()).isNotNull());
    }

    @Test
    @DisplayName("도감에 없는 음식은 UNMAPPED로 남긴다 — 수동 폴백·제보 대상")
    void 도감에_없으면_UNMAPPED로_남긴다() throws IOException {
        givenAiResponse("""
                {"foods":[{"candidates":[{"name":"마라샹궈","confidence":0.88}]}]}""");

        VisionAnalysisResponse response = service.analyze(List.of(jpeg()), null);

        FoodCandidate candidate = response.foods().getFirst().candidates().getFirst();
        assertThat(candidate.matchType()).isEqualTo("UNMAPPED");
        assertThat(candidate.slotId()).isNull();
    }

    @Test
    @DisplayName("후보가 빈 음식 항목은 버린다")
    void 빈_후보를_걸러낸다() throws IOException {
        givenAiResponse("""
                {"foods":[{"candidates":[]},{"candidates":[{"name":"떡볶이","confidence":0.9}]}]}""");

        VisionAnalysisResponse response = service.analyze(List.of(jpeg()), null);

        assertThat(response.foods()).hasSize(1);
    }

    @Test
    @DisplayName("계측값을 채운다 — 스파이크의 실제 산출물")
    void 계측값을_채운다() throws IOException {
        givenAiResponse("""
                {"foods":[{"candidates":[{"name":"라면","confidence":0.9}]}]}""");

        VisionAnalysisResponse response = service.analyze(List.of(jpeg(), jpeg()), null);

        assertThat(response.metrics().imageCount()).isEqualTo(2);
        assertThat(response.metrics().totalMs()).isGreaterThanOrEqualTo(response.metrics().preprocessMs());
        assertThat(response.metrics().originalBytes()).isPositive();
        assertThat(response.metrics().encodedBytes()).isPositive();
        assertThat(response.metrics().totalTokens()).isEqualTo(1300);
        assertThat(response.metrics().model()).isEqualTo("meta-llama/llama-4-scout-17b-16e-instruct");
    }

    @Test
    @DisplayName("사진이 없으면 AI를 호출하지 않는다")
    void 사진이_없으면_호출하지_않는다() {
        assertThatThrownBy(() -> service.analyze(List.of(), null))
                .isInstanceOf(VisionSpikeException.class)
                .hasFieldOrPropertyWithValue("code", "IMAGE_REQUIRED");

        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    @DisplayName("사진이 상한을 넘으면 AI를 호출하지 않는다 — 기획 1~5장, Groq 요청당 이미지 상한과도 일치")
    void 사진이_많으면_호출하지_않는다() throws IOException {
        List<MultipartFile> six = List.of(jpeg(), jpeg(), jpeg(), jpeg(), jpeg(), jpeg());

        assertThatThrownBy(() -> service.analyze(six, null))
                .isInstanceOf(VisionSpikeException.class)
                .hasFieldOrPropertyWithValue("code", "IMAGE_COUNT_EXCEEDED");

        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    @DisplayName("AI 호출이 실패하면 전용 코드로 감싼다")
    void AI_호출_실패를_감싼다() throws IOException {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("429 Too Many Requests"));

        assertThatThrownBy(() -> service.analyze(List.of(jpeg()), null))
                .isInstanceOf(VisionSpikeException.class)
                .hasFieldOrPropertyWithValue("code", "AI_CALL_FAILED");
    }

    @Test
    @DisplayName("AI 원문을 그대로 돌려준다 — 프롬프트 튜닝 근거")
    void AI_원문을_보존한다() throws IOException {
        String raw = """
                {"foods":[{"candidates":[{"name":"호떡","confidence":0.97}]}]}""";
        givenAiResponse(raw);

        VisionAnalysisResponse response = service.analyze(List.of(jpeg()), "호떡");

        assertThat(response.rawAiText()).isEqualTo(raw);
    }

    private void givenAiResponse(String content) {
        ChatResponse response = ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(content))))
                .metadata(ChatResponseMetadata.builder()
                        .model("meta-llama/llama-4-scout-17b-16e-instruct")
                        .usage(new DefaultUsage(1200, 100, 1300))
                        .build())
                .build();

        when(chatModel.call(any(Prompt.class))).thenReturn(response);
    }

    private static MockMultipartFile jpeg() throws IOException {
        BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return new MockMultipartFile("images", "food.jpg", "image/jpeg", out.toByteArray());
    }
}
