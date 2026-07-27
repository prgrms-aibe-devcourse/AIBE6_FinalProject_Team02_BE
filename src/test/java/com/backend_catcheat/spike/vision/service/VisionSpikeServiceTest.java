package com.backend_catcheat.spike.vision.service;

import com.backend_catcheat.spike.vision.config.VisionSpikeProperties;
import com.backend_catcheat.spike.vision.dto.VisionAnalysisResponse;
import com.backend_catcheat.spike.vision.dto.VisionAnalysisResponse.FoodVerdict;
import com.backend_catcheat.spike.vision.exception.VisionSpikeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
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
        VisionSpikeProperties properties =
                new VisionSpikeProperties(5, 1024, 0.8f, 3_500_000L, 5, true, "none");
        chatModel = mock(ChatModel.class);
        when(chatModel.getOptions())
                .thenReturn(OpenAiChatOptions.builder().model("qwen/qwen3.6-27b").build());

        service = new VisionSpikeService(
                new ImagePreprocessor(properties),
                new VisionAnalyzer(chatModel, new ObjectMapper(), properties, "dummy-key"),
                new DexMatcher(new DexCatalog(new ObjectMapper())),
                properties
        );
    }

    @Test
    @DisplayName("이름이 사진과 일치하고 도감에도 있으면 해금 가능하다")
    void 일치하면_해금_가능하다() throws IOException {
        givenAiResponse("""
                {"verdicts":[{"name":"김밥","matched":true,"confidence":0.95,"reason":""}]}""");

        VisionAnalysisResponse response = service.analyze(List.of(jpeg()), null, List.of("김밥"));

        FoodVerdict verdict = response.verdicts().getFirst();
        assertThat(verdict.requestedName()).isEqualTo("김밥");
        assertThat(verdict.matched()).isTrue();
        assertThat(verdict.slotName()).isEqualTo("김밥");
        assertThat(verdict.matchType()).isEqualTo("EXACT");
        assertThat(verdict.unlockable()).isTrue();
    }

    @Test
    @DisplayName("불일치면 해금하지 않고 사유를 전달한다")
    void 불일치면_해금하지_않는다() throws IOException {
        givenAiResponse("""
                {"verdicts":[{"name":"삼겹살","matched":false,"confidence":0.98,"reason":"사진은 김밥으로 보여요"}]}""");

        VisionAnalysisResponse response = service.analyze(List.of(jpeg()), null, List.of("삼겹살"));

        FoodVerdict verdict = response.verdicts().getFirst();
        assertThat(verdict.matched()).isFalse();
        assertThat(verdict.unlockable()).isFalse();
        assertThat(verdict.reason()).isEqualTo("사진은 김밥으로 보여요");
    }

    @Test
    @DisplayName("검증은 통과해도 도감에 없으면 해금할 수 없다")
    void 도감에_없으면_해금할_수_없다() throws IOException {
        givenAiResponse("""
                {"verdicts":[{"name":"마라샹궈","matched":true,"confidence":0.9,"reason":""}]}""");

        VisionAnalysisResponse response = service.analyze(List.of(jpeg()), null, List.of("마라샹궈"));

        FoodVerdict verdict = response.verdicts().getFirst();
        assertThat(verdict.matched()).isTrue();
        assertThat(verdict.matchType()).isEqualTo("UNMAPPED");
        assertThat(verdict.unlockable()).isFalse();
    }

    @Test
    @DisplayName("한 상 사진은 이름마다 개별 판정한다 — 통과한 것만 해금")
    void 한_상_사진을_개별_판정한다() throws IOException {
        givenAiResponse("""
                {"verdicts":[
                  {"name":"삼겹살","matched":true,"confidence":0.93,"reason":""},
                  {"name":"냉면","matched":true,"confidence":0.88,"reason":""},
                  {"name":"라면","matched":false,"confidence":0.91,"reason":"사진에 라면이 없어요"}
                ]}""");

        VisionAnalysisResponse response =
                service.analyze(List.of(jpeg()), null, List.of("삼겹살", "냉면", "라면"));

        assertThat(response.verdicts()).hasSize(3);
        assertThat(response.verdicts()).extracting(FoodVerdict::unlockable)
                .containsExactly(true, true, false);
    }

    @Test
    @DisplayName("AI가 판정을 빠뜨린 이름은 불일치로 처리한다 — 미검증 칸을 열지 않는다")
    void 판정_누락은_불일치다() throws IOException {
        givenAiResponse("""
                {"verdicts":[{"name":"김밥","matched":true,"confidence":0.95,"reason":""}]}""");

        VisionAnalysisResponse response =
                service.analyze(List.of(jpeg()), null, List.of("김밥", "떡볶이"));

        assertThat(response.verdicts()).hasSize(2);
        assertThat(response.verdicts().get(1).requestedName()).isEqualTo("떡볶이");
        assertThat(response.verdicts().get(1).matched()).isFalse();
        assertThat(response.verdicts().get(1).unlockable()).isFalse();
    }

    @Test
    @DisplayName("AI가 이름의 공백·표기를 바꿔 답해도 요청한 이름과 맞춘다")
    void 표기가_달라도_대응시킨다() throws IOException {
        givenAiResponse("""
                {"verdicts":[{"name":"김 밥","matched":true,"confidence":0.9,"reason":""}]}""");

        VisionAnalysisResponse response = service.analyze(List.of(jpeg()), null, List.of("김밥"));

        assertThat(response.verdicts().getFirst().matched()).isTrue();
    }

    @Test
    @DisplayName("계측값을 채운다 — 스파이크의 실제 산출물")
    void 계측값을_채운다() throws IOException {
        givenAiResponse("""
                {"verdicts":[{"name":"라면","matched":true,"confidence":0.9,"reason":""}]}""");

        VisionAnalysisResponse response =
                service.analyze(List.of(jpeg(), jpeg()), null, List.of("라면"));

        assertThat(response.metrics().uploadedImageCount()).isEqualTo(2);
        assertThat(response.metrics().analyzedImageCount()).isEqualTo(1);
        assertThat(response.metrics().totalMs()).isGreaterThanOrEqualTo(response.metrics().preprocessMs());
        assertThat(response.metrics().totalTokens()).isEqualTo(1300);
        assertThat(response.metrics().model()).isEqualTo("qwen/qwen3.6-27b");
    }

    @Test
    @DisplayName("사진을 5장 올려도 AI에는 분석 사진 1장만 보낸다")
    void AI에는_한_장만_보낸다() throws IOException {
        givenAiResponse("""
                {"verdicts":[{"name":"삼겹살","matched":true,"confidence":0.9,"reason":""}]}""");

        service.analyze(List.of(jpeg(), jpeg(), jpeg(), jpeg(), jpeg()), null, List.of("삼겹살"));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        assertThat(captor.getValue().getUserMessage().getMedia()).hasSize(1);
    }

    @Test
    @DisplayName("지정한 위치의 사진을 분석 사진으로 쓴다")
    void 지정한_분석_사진을_쓴다() throws IOException {
        givenAiResponse("""
                {"verdicts":[{"name":"김밥","matched":true,"confidence":0.9,"reason":""}]}""");
        // 크기를 다르게 만들어 어느 사진이 쓰였는지 originalBytes로 식별한다
        MockMultipartFile target = jpeg(1600, 1200);

        VisionAnalysisResponse response =
                service.analyze(List.of(jpeg(), jpeg(), target), 2, List.of("김밥"));

        assertThat(response.metrics().originalBytes()).isEqualTo(target.getSize());
        assertThat(response.metrics().uploadedImageCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("요청한 이름이 사용자 메시지에 실린다")
    void 요청한_이름을_보낸다() throws IOException {
        givenAiResponse("""
                {"verdicts":[{"name":"김밥","matched":true,"confidence":0.9,"reason":""}]}""");

        service.analyze(List.of(jpeg()), null, List.of("김밥", "떡볶이"));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        assertThat(captor.getValue().getUserMessage().getText()).contains("김밥", "떡볶이");
    }

    @Test
    @DisplayName("음식 이름이 없으면 AI를 호출하지 않는다")
    void 이름이_없으면_호출하지_않는다() throws IOException {
        List<MultipartFile> one = List.of(jpeg());

        assertThatThrownBy(() -> service.analyze(one, null, List.of()))
                .isInstanceOf(VisionSpikeException.class)
                .hasFieldOrPropertyWithValue("code", "FOOD_NAME_REQUIRED");

        assertThatThrownBy(() -> service.analyze(one, null, List.of("  ")))
                .isInstanceOf(VisionSpikeException.class)
                .hasFieldOrPropertyWithValue("code", "FOOD_NAME_REQUIRED");

        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    @DisplayName("음식 이름이 상한을 넘으면 AI를 호출하지 않는다")
    void 이름이_많으면_호출하지_않는다() throws IOException {
        List<MultipartFile> one = List.of(jpeg());
        List<String> six = List.of("가", "나", "다", "라", "마", "바");

        assertThatThrownBy(() -> service.analyze(one, null, six))
                .isInstanceOf(VisionSpikeException.class)
                .hasFieldOrPropertyWithValue("code", "FOOD_NAME_COUNT_EXCEEDED");

        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    @DisplayName("사진이 없으면 AI를 호출하지 않는다")
    void 사진이_없으면_호출하지_않는다() {
        assertThatThrownBy(() -> service.analyze(List.of(), null, List.of("김밥")))
                .isInstanceOf(VisionSpikeException.class)
                .hasFieldOrPropertyWithValue("code", "IMAGE_REQUIRED");

        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    @DisplayName("사진이 상한을 넘으면 AI를 호출하지 않는다")
    void 사진이_많으면_호출하지_않는다() throws IOException {
        List<MultipartFile> six = List.of(jpeg(), jpeg(), jpeg(), jpeg(), jpeg(), jpeg());

        assertThatThrownBy(() -> service.analyze(six, null, List.of("김밥")))
                .isInstanceOf(VisionSpikeException.class)
                .hasFieldOrPropertyWithValue("code", "IMAGE_COUNT_EXCEEDED");

        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    @DisplayName("분석 사진 위치가 범위를 벗어나면 AI를 호출하지 않는다")
    void 잘못된_분석_사진_위치를_거부한다() throws IOException {
        List<MultipartFile> two = List.of(jpeg(), jpeg());

        assertThatThrownBy(() -> service.analyze(two, 2, List.of("김밥")))
                .isInstanceOf(VisionSpikeException.class)
                .hasFieldOrPropertyWithValue("code", "ANALYSIS_PHOTO_INDEX_INVALID");

        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    @DisplayName("AI 호출이 실패하면 전용 코드로 감싼다")
    void AI_호출_실패를_감싼다() throws IOException {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("429 Too Many Requests"));

        assertThatThrownBy(() -> service.analyze(List.of(jpeg()), null, List.of("김밥")))
                .isInstanceOf(VisionSpikeException.class)
                .hasFieldOrPropertyWithValue("code", "AI_CALL_FAILED");
    }

    @Test
    @DisplayName("AI 원문을 그대로 돌려준다 — 프롬프트 튜닝 근거")
    void AI_원문을_보존한다() throws IOException {
        String raw = """
                {"verdicts":[{"name":"호떡","matched":true,"confidence":0.97,"reason":""}]}""";
        givenAiResponse(raw);

        VisionAnalysisResponse response = service.analyze(List.of(jpeg()), null, List.of("호떡"));

        assertThat(response.rawAiText()).isEqualTo(raw);
    }

    private void givenAiResponse(String content) {
        ChatResponse response = ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(content))))
                .metadata(ChatResponseMetadata.builder()
                        .model("qwen/qwen3.6-27b")
                        .usage(new DefaultUsage(1200, 100, 1300))
                        .build())
                .build();

        when(chatModel.call(any(Prompt.class))).thenReturn(response);
    }

    private static MockMultipartFile jpeg() throws IOException {
        return jpeg(800, 600);
    }

    private static MockMultipartFile jpeg(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return new MockMultipartFile("images", "food.jpg", "image/jpeg", out.toByteArray());
    }
}
