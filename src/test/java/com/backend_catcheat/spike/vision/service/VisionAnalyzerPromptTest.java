package com.backend_catcheat.spike.vision.service;

import com.backend_catcheat.spike.vision.config.VisionSpikeProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AI 요청 구성")
class VisionAnalyzerPromptTest {

    private static final String CONFIGURED_MODEL = "qwen/qwen3.6-27b";

    private ChatModel chatModel;

    private VisionAnalyzer analyzerWith(boolean jsonMode) {
        chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder()
                .model(CONFIGURED_MODEL)
                .temperature(0.2)
                .build());
        when(chatModel.call(any(Prompt.class))).thenReturn(ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage("{\"verdicts\":[]}"))))
                .build());

        return new VisionAnalyzer(
                chatModel,
                new ObjectMapper(),
                new VisionSpikeProperties(5, 1024, 0.8f, 3_500_000L, 5, jsonMode, "none"),
                "dummy-key"
        );
    }

    private Prompt capturePrompt(VisionAnalyzer analyzer) {
        analyzer.analyze(List.of(new PreparedImage(new byte[]{1, 2, 3}, 100L, 64, 64, "food.jpg")), List.of("김밥"));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("설정된 모델과 temperature가 유지된다 — 기본 옵션이 덮이지 않는다")
    void 설정된_모델을_유지한다() {
        OpenAiChatOptions options = (OpenAiChatOptions) capturePrompt(analyzerWith(true)).getOptions();

        assertThat(options.getModel()).isEqualTo(CONFIGURED_MODEL);
        assertThat(options.getTemperature()).isEqualTo(0.2);
    }

    @Test
    @DisplayName("json-mode가 켜지면 JSON_OBJECT 응답 형식을 요청한다")
    void JSON_모드를_요청한다() {
        OpenAiChatOptions options = (OpenAiChatOptions) capturePrompt(analyzerWith(true)).getOptions();

        assertThat(options.getResponseFormat()).isNotNull();
        assertThat(options.getResponseFormat().getType())
                .isEqualTo(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT);
    }

    @Test
    @DisplayName("json-mode가 꺼지면 응답 형식을 지정하지 않는다 — 모델은 그대로 유지")
    void JSON_모드를_끌_수_있다() {
        OpenAiChatOptions options = (OpenAiChatOptions) capturePrompt(analyzerWith(false)).getOptions();

        assertThat(options.getResponseFormat()).isNull();
        assertThat(options.getModel()).isEqualTo(CONFIGURED_MODEL);
    }

    @Test
    @DisplayName("추론을 끈 채로 요청한다 — 사고 과정이 길면 JSON이 잘려 검증이 실패한다")
    void 추론을_끈다() {
        OpenAiChatOptions options = (OpenAiChatOptions) capturePrompt(analyzerWith(true)).getOptions();

        assertThat(options.getReasoningEffort()).isEqualTo("none");
    }

    @Test
    @DisplayName("시스템 프롬프트와 이미지가 함께 실린다")
    void 시스템_프롬프트와_이미지를_싣는다() {
        Prompt prompt = capturePrompt(analyzerWith(true));

        assertThat(prompt.getInstructions()).hasSize(2);
        assertThat(prompt.getSystemMessage().getText()).contains("JSON");
        assertThat(prompt.getUserMessage().getMedia()).hasSize(1);
    }

    @Test
    @DisplayName("검증할 음식 이름을 모두 사용자 메시지에 싣는다")
    void 검증할_이름을_싣는다() {
        VisionAnalyzer analyzer = analyzerWith(true);
        analyzer.analyze(
                List.of(new PreparedImage(new byte[]{1}, 10L, 8, 8, "a.jpg")),
                List.of("삼겹살", "비빔냉면", "된장찌개"));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());

        assertThat(captor.getValue().getUserMessage().getText())
                .contains("삼겹살", "비빔냉면", "된장찌개");
    }
}
