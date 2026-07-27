package com.backend_catcheat.spike.vision.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiVerificationResult(List<AiVerdict> verdicts) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiVerdict(String name, boolean matched, double confidence, String reason) {
    }
}
