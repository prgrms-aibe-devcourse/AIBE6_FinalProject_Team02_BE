package com.backend_catcheat.domain.illustration.service;

import com.backend_catcheat.domain.illustration.entity.IllustrationJob;
import com.backend_catcheat.domain.illustration.entity.IllustrationMode;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class PromptComposer {

    private static final String BASE_PATH = "prompts/illustration/";

    private String stylizeAny;
    private String badgeEmblem;
    private String generateBadge;

    // 파일이 없으면 생성 도중이 아니라 뜰 때 터지게 한다
    @PostConstruct
    void load() {
        stylizeAny = read("stylize-any.txt");
        badgeEmblem = read("badge-emblem.txt");
        generateBadge = read("generate-badge.txt");
    }

    public String compose(IllustrationJob job) {
        StringBuilder prompt = new StringBuilder(templateFor(job));

        if (job.getDescription() != null && !job.getDescription().isBlank()) {
            prompt.append("\n\n대상: ").append(job.getDescription().strip());
        }

        // 수정 지시는 맨 뒤에 붙어야 앞의 규칙을 덮는다
        if (job.getInstructions() != null && !job.getInstructions().isBlank()) {
            prompt.append("\n\n[추가 요청 — 아래를 반영해줘]\n").append(job.getInstructions().strip());
        }

        return prompt.toString();
    }

    // 뱃지만 원형으로 크롭돼 배치가 다르다. 화풍 지시는 셋이 같다
    private String templateFor(IllustrationJob job) {
        if (job.getMode() == IllustrationMode.GENERATE) {
            return generateBadge;
        }
        return job.getPurpose().isBadge() ? badgeEmblem : stylizeAny;
    }

    private String read(String fileName) {
        ClassPathResource resource = new ClassPathResource(BASE_PATH + fileName);
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            log.error("[일러스트] 프롬프트 파일을 읽지 못했습니다 file={}", fileName, e);
            throw new CustomException(ErrorCode.ILLUSTRATION_FAILED);
        }
    }
}
