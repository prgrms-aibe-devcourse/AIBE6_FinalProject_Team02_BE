package com.backend_catcheat.spike.vision.service;

import com.backend_catcheat.spike.vision.config.VisionSpikeProperties;
import com.backend_catcheat.spike.vision.dto.SpikeMetrics;
import com.backend_catcheat.spike.vision.dto.VisionAnalysisResponse;
import com.backend_catcheat.spike.vision.dto.VisionAnalysisResponse.FoodVerdict;

import com.backend_catcheat.spike.vision.dto.ai.AiVerificationResult;
import com.backend_catcheat.spike.vision.dto.ai.AiVerificationResult.AiVerdict;
import com.backend_catcheat.spike.vision.exception.VisionSpikeException;
import com.backend_catcheat.spike.vision.service.DexMatcher.MatchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class VisionSpikeService {

    private static final Logger log = LoggerFactory.getLogger(VisionSpikeService.class);

    private final ImagePreprocessor preprocessor;
    private final VisionAnalyzer analyzer;
    private final DexMatcher dexMatcher;
    private final VisionSpikeProperties properties;

    public VisionSpikeService(
            ImagePreprocessor preprocessor,
            VisionAnalyzer analyzer,
            DexMatcher dexMatcher,
            VisionSpikeProperties properties
    ) {
        this.preprocessor = preprocessor;
        this.analyzer = analyzer;
        this.dexMatcher = dexMatcher;
        this.properties = properties;
    }

    public VisionAnalysisResponse analyze(List<MultipartFile> images, Integer analysisPhotoIndex, List<String> foodNames) {
        validateImageCount(images);
        List<String> names = normalizeFoodNames(foodNames);
        int index = resolveAnalysisPhotoIndex(images, analysisPhotoIndex);
        long startedAt = System.nanoTime();

        // AI에는 분석 사진 1장만 보낸다 (AGENTS.md §5.2). 나머지는 카드 사진이라 전처리 대상이 아니다.
        long preprocessStart = System.nanoTime();
        PreparedImage analysisPhoto = preprocessor.prepare(images.get(index));
        long preprocessMs = elapsedMs(preprocessStart);

        List<PreparedImage> prepared = List.of(analysisPhoto);

        long aiStart = System.nanoTime();
        VisionAnalyzer.AnalysisOutcome outcome = analyzer.analyze(prepared, names);
        long aiCallMs = elapsedMs(aiStart);

        long parseStart = System.nanoTime();
        AiVerificationResult aiResult = analyzer.parse(outcome.rawText());
        long parseMs = elapsedMs(parseStart);

        long matchStart = System.nanoTime();
        List<FoodVerdict> verdicts = toVerdicts(names, aiResult);
        long matchMs = elapsedMs(matchStart);

        SpikeMetrics metrics = buildMetrics(
                images.size(), prepared, outcome.response(),
                preprocessMs, aiCallMs, parseMs, matchMs, elapsedMs(startedAt));

        logOutcome(verdicts, metrics);

        return new VisionAnalysisResponse(verdicts, metrics, outcome.rawText());
    }

    private List<String> normalizeFoodNames(List<String> foodNames) {
        if (foodNames == null || foodNames.isEmpty()) {
            throw new VisionSpikeException("FOOD_NAME_REQUIRED", "음식 이름을 입력해 주세요");
        }
        List<String> names = foodNames.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (names.isEmpty()) {
            throw new VisionSpikeException("FOOD_NAME_REQUIRED", "음식 이름을 입력해 주세요");
        }
        if (names.size() > properties.maxFoodNames()) {
            throw new VisionSpikeException("FOOD_NAME_COUNT_EXCEEDED",
                    "음식은 한 번에 최대 %d개까지 등록할 수 있어요".formatted(properties.maxFoodNames()));
        }
        return names;
    }

    private List<FoodVerdict> toVerdicts(List<String> requestedNames, AiVerificationResult aiResult) {
        Map<String, AiVerdict> byName = aiResult.verdicts().stream()
                .filter(v -> v != null && StringUtils.hasText(v.name()))
                .collect(Collectors.toMap(
                        v -> normalizeName(v.name()), v -> v, (first, second) -> first));

        return requestedNames.stream()
                .map(name -> toVerdict(name, byName.get(normalizeName(name))))
                .toList();
    }

    private FoodVerdict toVerdict(String requestedName, AiVerdict aiVerdict) {
        boolean matched = aiVerdict != null && aiVerdict.matched();
        double confidence = aiVerdict != null ? aiVerdict.confidence() : 0.0;
        String reason = aiVerdict == null
                ? "사진에서 이 음식을 확인하지 못했어요"
                : (matched ? "" : defaultIfBlank(aiVerdict.reason(), "사진과 음식 이름이 달라 보여요"));

        MatchResult match = dexMatcher.match(requestedName);
        return new FoodVerdict(
                requestedName,
                matched,
                confidence,
                reason,
                match.isMapped() ? match.slot().id() : null,
                match.isMapped() ? match.slot().name() : null,
                match.isMapped() ? match.slot().category() : null,
                match.matchType().name(),
                // 검증을 통과했고 도감에도 있어야 해금할 수 있다
                matched && match.isMapped()
        );
    }

    private static String normalizeName(String value) {
        return value.replaceAll("[\\s·\\-_]", "").toLowerCase();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private void validateImageCount(List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            throw new VisionSpikeException("IMAGE_REQUIRED", "사진을 최소 1장 올려 주세요");
        }
        if (images.size() > properties.maxImages()) {
            throw new VisionSpikeException("IMAGE_COUNT_EXCEEDED",
                    "사진은 최대 %d장까지 올릴 수 있어요".formatted(properties.maxImages()));
        }
    }

    private int resolveAnalysisPhotoIndex(List<MultipartFile> images, Integer requested) {
        if (requested == null) {
            return 0;
        }
        if (requested < 0 || requested >= images.size()) {
            throw new VisionSpikeException("ANALYSIS_PHOTO_INDEX_INVALID",
                    "분석할 사진을 다시 선택해 주세요");
        }
        return requested;
    }

    private SpikeMetrics buildMetrics(
            int uploadedCount,
            List<PreparedImage> prepared,
            ChatResponse response,
            long preprocessMs,
            long aiCallMs,
            long parseMs,
            long matchMs,
            long totalMs
    ) {
        ChatResponseMetadata metadata = response.getMetadata();
        Usage usage = metadata != null ? metadata.getUsage() : null;

        return new SpikeMetrics(
                preprocessMs,
                aiCallMs,
                parseMs,
                matchMs,
                totalMs,
                uploadedCount,
                prepared.size(),
                prepared.stream().mapToLong(PreparedImage::originalBytes).sum(),
                prepared.stream().mapToLong(PreparedImage::encodedBytes).sum(),
                usage != null ? usage.getPromptTokens() : null,
                usage != null ? usage.getCompletionTokens() : null,
                usage != null ? usage.getTotalTokens() : null,
                metadata != null ? metadata.getModel() : null
        );
    }

    private void logOutcome(List<FoodVerdict> verdicts, SpikeMetrics metrics) {
        long matched = verdicts.stream().filter(FoodVerdict::matched).count();
        long unlockable = verdicts.stream().filter(FoodVerdict::unlockable).count();

        log.info("[spike] 검증 완료 model={} 업로드{}장/분석{}장 요청{}건 일치{}건 해금가능{}건 | 전처리 {}ms + AI {}ms + 파싱 {}ms + 매핑 {}ms = 총 {}ms | 토큰 {} | {}B -> {}B",
                metrics.model(), metrics.uploadedImageCount(), metrics.analyzedImageCount(),
                verdicts.size(), matched, unlockable,
                metrics.preprocessMs(), metrics.aiCallMs(), metrics.parseMs(), metrics.matchMs(), metrics.totalMs(),
                metrics.totalTokens(), metrics.originalBytes(), metrics.encodedBytes());

        if (metrics.totalMs() > 5000) {
            log.warn("[spike] 목표 응답시간 5초 초과: {}ms", metrics.totalMs());
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
