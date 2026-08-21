package com.backend_catcheat.domain.registration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "catcheat.heic")
public record HeicProperties(
        String binaryPath,
        int quality,
        long timeoutMs
) {
}
