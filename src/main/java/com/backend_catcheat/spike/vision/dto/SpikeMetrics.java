package com.backend_catcheat.spike.vision.dto;

public record SpikeMetrics(
        long preprocessMs,
        long aiCallMs,
        long parseMs,
        long matchMs,
        long totalMs,
        int imageCount,
        long originalBytes,
        long encodedBytes,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        String model
) {
}
