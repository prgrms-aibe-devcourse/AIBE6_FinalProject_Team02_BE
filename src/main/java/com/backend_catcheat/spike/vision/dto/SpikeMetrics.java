package com.backend_catcheat.spike.vision.dto;

public record SpikeMetrics(
        long preprocessMs,
        long aiCallMs,
        long parseMs,
        long matchMs,
        long totalMs,
        /** 유저가 업로드한 사진 수 (카드 사진 후보) */
        int uploadedImageCount,
        /** 실제로 AI에 보낸 사진 수 */
        int analyzedImageCount,
        long originalBytes,
        long encodedBytes,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        String model
) {
}
