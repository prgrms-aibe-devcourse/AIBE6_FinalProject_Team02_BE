package com.backend_catcheat.spike.vision.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Groq이 반환하는 원문 JSON의 매핑 대상. 도감 매핑 전 단계이므로 API 응답 DTO와 분리한다.
 * <p>
 * 기획안의 분석 결과 3형태(단일 확신 / 유사 후보 / 복수 음식 검출)는 별도 타입이 아니라
 * 이 한 구조의 표현이다 — 후보 1개면 단일 확신, 2~3개면 유사 후보, foods가 2개 이상이면 한 상 사진.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiVisionResult(List<AiFood> foods) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiFood(List<AiCandidate> candidates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiCandidate(String name, double confidence) {
    }
}
