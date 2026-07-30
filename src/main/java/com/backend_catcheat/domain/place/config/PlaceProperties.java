package com.backend_catcheat.domain.place.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param size           카카오 키워드 검색 상한은 15
 * @param minQueryLength 이 길이 미만이면 외부 호출 없이 빈 결과를 준다
 */
@ConfigurationProperties(prefix = "catcheat.place")
public record PlaceProperties(
        String apiKey,
        int size,
        int minQueryLength,
        long timeoutMs
) {
}
