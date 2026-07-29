package com.backend_catcheat.domain.registration.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** AI 원문 응답의 형태. 프롬프트(food-verification-system.st)의 출력 규격과 1:1로 대응한다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiVerificationResult(List<AiVerdict> verdicts) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiVerdict(String name, boolean matched, double confidence, String reason) {
    }
}
