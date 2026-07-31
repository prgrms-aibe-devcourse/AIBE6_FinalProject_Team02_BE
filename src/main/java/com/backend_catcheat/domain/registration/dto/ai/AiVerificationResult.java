package com.backend_catcheat.domain.registration.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

// 필드를 바꾸면 schemas/food-verification.json도 같이 고친다
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiVerificationResult(List<AiVerdict> verdicts) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiVerdict(String name, boolean matched, double confidence, String reason) {
    }
}
