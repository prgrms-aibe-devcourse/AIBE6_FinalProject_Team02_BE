package com.backend_catcheat.spike.vision;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.MimeTypeUtils;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("external")
@SpringBootTest
class GroqVisionSmokeTest {

    private static final Logger log = LoggerFactory.getLogger(GroqVisionSmokeTest.class);

    private static final String SAMPLE_IMAGE = "spike/sample-food.jpg";

    @Autowired
    private ChatModel chatModel;

    @Test
    void 텍스트_호출이_성공한다() {
        String answer = chatModel.call("한국 음식 이름 하나만 답해라. 다른 말은 하지 마라.");

        log.info("[smoke] 텍스트 응답: {}", answer);
        assertThat(answer).isNotBlank();
    }

    @Test
    void 이미지_호출이_성공한다() {
        ClassPathResource image = new ClassPathResource(SAMPLE_IMAGE);
        assumeTrue(image.exists(),
                () -> "표본 사진이 없어 건너뜁니다. src/test/resources/%s 에 음식 사진을 두세요.".formatted(SAMPLE_IMAGE));

        UserMessage message = UserMessage.builder()
                .text("이 사진에 보이는 음식이 무엇인지 한국어로 답해라.")
                .media(Media.builder()
                        .mimeType(MimeTypeUtils.IMAGE_JPEG)
                        .data(image)
                        .build())
                .build();

        Instant start = Instant.now();
        ChatResponse response = chatModel.call(new Prompt(message));
        Duration elapsed = Duration.between(start, Instant.now());

        String content = response.getResult().getOutput().getText();
        Usage usage = response.getMetadata().getUsage();

        log.info("[smoke] 비전 응답: {}", content);
        log.info("[smoke] 지연시간: {}ms", elapsed.toMillis());
        log.info("[smoke] 토큰: prompt={}, completion={}, total={}",
                usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());

        assertThat(content).isNotBlank();
    }
}
