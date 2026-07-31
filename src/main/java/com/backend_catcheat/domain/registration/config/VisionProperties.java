package com.backend_catcheat.domain.registration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "catcheat.vision")
public record VisionProperties(
        int maxImages,
        int maxLongEdgePx,
        float jpegQuality,
        long maxEncodedBytes,
        int maxFoodNames,
        String reasoningEffort
) {
}
