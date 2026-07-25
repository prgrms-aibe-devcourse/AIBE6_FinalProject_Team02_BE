package com.backend_catcheat.spike.vision.dto;

import java.util.List;

public record VisionAnalysisResponse(
        List<DetectedFood> foods,
        SpikeMetrics metrics,
        String rawAiText
) {

    public record DetectedFood(List<FoodCandidate> candidates) {
    }

    public record FoodCandidate(
            String aiName,
            double confidence,
            Long slotId,
            String slotName,
            String category,
            String matchType
    ) {
    }
}
