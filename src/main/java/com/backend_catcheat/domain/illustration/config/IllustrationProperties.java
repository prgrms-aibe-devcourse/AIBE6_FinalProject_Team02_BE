package com.backend_catcheat.domain.illustration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param sourceMaxEdgePx 이 값 없이 스마트폰 원본을 보내면 크기 때문에 거절된다
 * @param storedMaxEdgePx 생성 원본 1024px는 2.4MB라 그대로 저장하지 않는다
 * @param dailyLimit      생성 실패도 비용이 나가므로 실패 건도 센다
 */
@ConfigurationProperties(prefix = "catcheat.illustration")
public record IllustrationProperties(
        String apiKey,
        String model,
        String quality,
        int sourceMaxEdgePx,
        int resultSizePx,
        int storedMaxEdgePx,
        int dailyLimit,
        long timeoutMs
) {
}
