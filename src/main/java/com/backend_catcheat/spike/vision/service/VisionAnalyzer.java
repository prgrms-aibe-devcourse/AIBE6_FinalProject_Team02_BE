package com.backend_catcheat.spike.vision.service;

import com.backend_catcheat.spike.vision.config.VisionSpikeProperties;
import com.backend_catcheat.spike.vision.dto.ai.AiVisionResult;
import com.backend_catcheat.spike.vision.exception.VisionSpikeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;


@Component
public class VisionAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(VisionAnalyzer.class);

    private static final String SYSTEM_PROMPT_PATH = "prompts/food-detection-system.st";

    /** application.yml의 api-key 기본값. 실제 키가 주입되지 않았음을 뜻한다. */
    private static final String MISSING_API_KEY = "missing-groq-api-key";

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final VisionSpikeProperties properties;
    private final String systemPrompt;
    private final String apiKey;

    public VisionAnalyzer(
            ChatModel chatModel,
            ObjectMapper objectMapper,
            VisionSpikeProperties properties,
            @Value("${spring.ai.openai.api-key:}") String apiKey
    ) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.apiKey = apiKey;
        this.systemPrompt = loadSystemPrompt();
    }

    public AnalysisOutcome analyze(List<PreparedImage> images, String hint) {
        requireApiKey();

        Prompt prompt = buildPrompt(images, hint);

        ChatResponse response;
        try {
            response = chatModel.call(prompt);
        } catch (Exception e) {
            throw new VisionSpikeException("AI_CALL_FAILED", "음식 분석에 실패했어요. 잠시 후 다시 시도해 주세요", e);
        }

        String rawText = response.getResult().getOutput().getText();
        log.debug("[spike] AI 원문 응답: {}", rawText);

        return new AnalysisOutcome(rawText, response);
    }

    public AiVisionResult parse(String rawText) {
        String json = extractJsonObject(rawText);
        try {
            AiVisionResult result = objectMapper.readValue(json, AiVisionResult.class);
            // foods 누락은 "음식을 못 찾음"과 같게 취급한다 — 재분석 플로우로 넘기면 되므로 예외로 볼 필요가 없다
            return result.foods() == null ? new AiVisionResult(List.of()) : result;
        } catch (JacksonException e) {
            log.warn("[spike] AI 응답 파싱 실패. 원문: {}", rawText);
            throw new VisionSpikeException("AI_RESPONSE_PARSE_FAILED", "음식 분석 결과를 읽지 못했어요", e);
        }
    }

    private void requireApiKey() {
        if (!StringUtils.hasText(apiKey) || MISSING_API_KEY.equals(apiKey)) {
            throw new VisionSpikeException("AI_KEY_MISSING",
                    "AI 설정이 준비되지 않았어요 (환경변수 GROQ_API_KEY를 설정해 주세요)");
        }
    }

    private Prompt buildPrompt(List<PreparedImage> images, String hint) {
        List<Media> media = images.stream()
                .map(image -> Media.builder()
                        .mimeType(MimeTypeUtils.IMAGE_JPEG)
                        .data(new ByteArrayResource(image.data()))
                        .build())
                .toList();

        String userText = StringUtils.hasText(hint)
                ? "이 사진 속 음식을 판별해라. 유저가 준 힌트: \"%s\". 힌트를 우선 고려하되 사진과 맞지 않으면 무시해라.".formatted(hint)
                : "이 사진 속 음식을 판별해라.";

        UserMessage userMessage = UserMessage.builder()
                .text(userText)
                .media(media)
                .build();

        return new Prompt(List.of(new SystemMessage(systemPrompt), userMessage), chatOptions());
    }

    private OpenAiChatOptions chatOptions() {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder();
        if (properties.jsonMode()) {
            builder.responseFormat(OpenAiChatModel.ResponseFormat.builder()
                    .type(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT)
                    .build());
        }
        return builder.build();
    }

    private String extractJsonObject(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            throw new VisionSpikeException("AI_RESPONSE_EMPTY", "음식 분석 결과가 비어 있어요");
        }
        int start = rawText.indexOf('{');
        int end = rawText.lastIndexOf('}');
        if (start < 0 || end <= start) {
            log.warn("[spike] AI 응답에서 JSON을 찾지 못했습니다. 원문: {}", rawText);
            throw new VisionSpikeException("AI_RESPONSE_PARSE_FAILED", "음식 분석 결과를 읽지 못했어요");
        }
        return rawText.substring(start, end + 1);
    }

    private static String loadSystemPrompt() {
        try {
            return new ClassPathResource(SYSTEM_PROMPT_PATH)
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("시스템 프롬프트를 읽을 수 없습니다: " + SYSTEM_PROMPT_PATH, e);
        }
    }

    public record AnalysisOutcome(String rawText, ChatResponse response) {
    }
}
