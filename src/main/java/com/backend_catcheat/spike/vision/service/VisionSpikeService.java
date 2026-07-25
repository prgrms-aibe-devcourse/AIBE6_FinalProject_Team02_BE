package com.backend_catcheat.spike.vision.service;

import com.backend_catcheat.spike.vision.config.VisionSpikeProperties;
import com.backend_catcheat.spike.vision.dto.SpikeMetrics;
import com.backend_catcheat.spike.vision.dto.VisionAnalysisResponse;
import com.backend_catcheat.spike.vision.dto.VisionAnalysisResponse.DetectedFood;
import com.backend_catcheat.spike.vision.dto.VisionAnalysisResponse.FoodCandidate;
import com.backend_catcheat.spike.vision.dto.ai.AiVisionResult;
import com.backend_catcheat.spike.vision.exception.VisionSpikeException;
import com.backend_catcheat.spike.vision.service.DexMatcher.MatchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

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

    public VisionAnalysisResponse analyze(List<MultipartFile> images, String hint) {
        validateImageCount(images);
        long startedAt = System.nanoTime();

        long preprocessStart = System.nanoTime();
        List<PreparedImage> prepared = images.stream().map(preprocessor::prepare).toList();
        long preprocessMs = elapsedMs(preprocessStart);

        long aiStart = System.nanoTime();
        VisionAnalyzer.AnalysisOutcome outcome = analyzer.analyze(prepared, hint);
        long aiCallMs = elapsedMs(aiStart);

        long parseStart = System.nanoTime();
        AiVisionResult aiResult = analyzer.parse(outcome.rawText());
        long parseMs = elapsedMs(parseStart);

        long matchStart = System.nanoTime();
        List<DetectedFood> foods = toDetectedFoods(aiResult);
        long matchMs = elapsedMs(matchStart);

        SpikeMetrics metrics = buildMetrics(
                prepared, outcome.response(), preprocessMs, aiCallMs, parseMs, matchMs, elapsedMs(startedAt));

        logOutcome(foods, metrics);

        return new VisionAnalysisResponse(foods, metrics, outcome.rawText());
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

    private List<DetectedFood> toDetectedFoods(AiVisionResult aiResult) {
        return aiResult.foods().stream()
                .filter(food -> food.candidates() != null && !food.candidates().isEmpty())
                .map(food -> new DetectedFood(
                        food.candidates().stream()
                                .filter(Objects::nonNull)
                                .sorted(Comparator.comparingDouble(AiVisionResult.AiCandidate::confidence).reversed())
                                .map(this::toCandidate)
                                .toList()))
                .toList();
    }

    private FoodCandidate toCandidate(AiVisionResult.AiCandidate candidate) {
        MatchResult match = dexMatcher.match(candidate.name());
        return new FoodCandidate(
                candidate.name(),
                candidate.confidence(),
                match.isMapped() ? match.slot().id() : null,
                match.isMapped() ? match.slot().name() : null,
                match.isMapped() ? match.slot().category() : null,
                match.matchType().name()
        );
    }

    private SpikeMetrics buildMetrics(
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
                prepared.size(),
                prepared.stream().mapToLong(PreparedImage::originalBytes).sum(),
                prepared.stream().mapToLong(PreparedImage::encodedBytes).sum(),
                usage != null ? usage.getPromptTokens() : null,
                usage != null ? usage.getCompletionTokens() : null,
                usage != null ? usage.getTotalTokens() : null,
                metadata != null ? metadata.getModel() : null
        );
    }

    private void logOutcome(List<DetectedFood> foods, SpikeMetrics metrics) {
        long unmapped = foods.stream()
                .flatMap(food -> food.candidates().stream())
                .filter(candidate -> DexMatcher.MatchType.UNMAPPED.name().equals(candidate.matchType()))
                .count();

        // 리포트 집계는 이 한 줄을 긁어서 만든다
        log.info("[spike] 분석 완료 model={} foods={} unmapped={} | 전처리 {}ms + AI {}ms + 파싱 {}ms + 매핑 {}ms = 총 {}ms | 토큰 {} | {}B -> {}B",
                metrics.model(), foods.size(), unmapped,
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
