package com.backend_catcheat.spike.vision.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "catcheat.vision")
public record VisionSpikeProperties(
        int maxImages,
        int maxLongEdgePx,
        float jpegQuality,
        long maxEncodedBytes,
        int maxFoodNames,
        boolean jsonMode,
        String reasoningEffort
) {
}
