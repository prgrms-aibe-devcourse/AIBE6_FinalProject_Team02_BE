package com.backend_catcheat.domain.registration.service;

import com.backend_catcheat.domain.registration.config.VisionProperties;
import com.backend_catcheat.domain.registration.dto.ai.AiVerificationResult;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
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

@Slf4j
@Component
public class VisionAnalyzer {

    private static final String SYSTEM_PROMPT_PATH = "prompts/food-verification-system.st";
    private static final String RESPONSE_SCHEMA_PATH = "schemas/food-verification.json";

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final VisionProperties properties;
    private final String systemPrompt;
    private final String responseSchema;
    private final String apiKey;

    public VisionAnalyzer(
            ChatModel chatModel,
            ObjectMapper objectMapper,
            VisionProperties properties,
            @Value("${spring.ai.openai.api-key}") String apiKey
    ) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.apiKey = apiKey;
        this.systemPrompt = readClasspath(SYSTEM_PROMPT_PATH);
        this.responseSchema = readClasspath(RESPONSE_SCHEMA_PATH);
    }

    public AiVerificationResult verify(PreparedImage image, List<String> foodNames) {
        requireApiKey();

        ChatResponse response;
        try {
            response = chatModel.call(buildPrompt(image, foodNames));
        } catch (Exception e) {
            log.error("[등록] AI 호출 실패", e);
            throw new CustomException(ErrorCode.AI_CALL_FAILED);
        }

        logTokenUsage(response);

        String rawText = response.getResult().getOutput().getText();
        log.debug("[등록] AI 원문 응답: {}", rawText);

        return parse(rawText);
    }

    // 종량제라 등록 1건당 실제 소비량을 남긴다. 단가는 바뀌므로 환산하지 않는다
    private void logTokenUsage(ChatResponse response) {
        ChatResponseMetadata metadata = response.getMetadata();
        Usage usage = metadata != null ? metadata.getUsage() : null;
        if (usage == null) {
            return;
        }
        log.info("[등록] AI 토큰 prompt={} completion={} total={}",
                usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }

    AiVerificationResult parse(String rawText) {
        String json = extractJsonObject(rawText);
        try {
            AiVerificationResult result = objectMapper.readValue(json, AiVerificationResult.class);
            // verdicts 누락은 "아무것도 검증하지 못함"이므로 전부 불일치로 다룬다
            return result.verdicts() == null ? new AiVerificationResult(List.of()) : result;
        } catch (JacksonException e) {
            log.warn("[등록] AI 응답 파싱 실패. 원문: {}", rawText);
            throw new CustomException(ErrorCode.AI_RESPONSE_PARSE_FAILED);
        }
    }

    private void requireApiKey() {
        if (!StringUtils.hasText(apiKey)) {
            throw new CustomException(ErrorCode.AI_KEY_MISSING);
        }
    }

    private Prompt buildPrompt(PreparedImage image, List<String> foodNames) {
        Media media = Media.builder()
                .mimeType(MimeTypeUtils.IMAGE_JPEG)
                .data(new ByteArrayResource(image.data()))
                .build();

        UserMessage userMessage = UserMessage.builder()
                .text("이 사진에 다음 음식이 있는지 각각 판정해라: %s".formatted(String.join(", ", foodNames)))
                .media(List.of(media))
                .build();

        return new Prompt(List.of(new SystemMessage(systemPrompt), userMessage), chatOptions());
    }

    private OpenAiChatOptions chatOptions() {
        OpenAiChatOptions.Builder builder = ((OpenAiChatOptions) chatModel.getOptions()).mutate();
        // jsonSchema()가 type을 JSON_SCHEMA로 바꾸고 strict=true까지 붙인다
        builder.responseFormat(OpenAiChatModel.ResponseFormat.builder()
                .jsonSchema(responseSchema)
                .build());
        // gpt-4o-mini는 이 파라미터를 받지 않아 실으면 400이 난다
        if (StringUtils.hasText(properties.reasoningEffort())) {
            builder.reasoningEffort(properties.reasoningEffort());
        }
        return builder.build();
    }

    private String extractJsonObject(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            throw new CustomException(ErrorCode.AI_RESPONSE_PARSE_FAILED);
        }
        int start = rawText.indexOf('{');
        int end = rawText.lastIndexOf('}');
        if (start < 0 || end <= start) {
            log.warn("[등록] AI 응답에서 JSON을 찾지 못했습니다. 원문: {}", rawText);
            throw new CustomException(ErrorCode.AI_RESPONSE_PARSE_FAILED);
        }
        return rawText.substring(start, end + 1);
    }

    private static String readClasspath(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("클래스패스 리소스를 읽을 수 없습니다: " + path, e);
        }
    }
}
