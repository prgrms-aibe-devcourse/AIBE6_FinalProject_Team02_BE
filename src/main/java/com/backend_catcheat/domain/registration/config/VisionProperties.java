package com.backend_catcheat.domain.registration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 스파이크에서 실측으로 굳힌 값들 (application.yml `catcheat.vision`).
 */
@ConfigurationProperties(prefix = "catcheat.vision")
public record VisionProperties(
        int maxImages,
        int maxLongEdgePx,
        float jpegQuality,
        long maxEncodedBytes,
        int maxFoodNames,
        boolean jsonMode,
        String reasoningEffort
) {
}
