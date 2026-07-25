package com.backend_catcheat.spike.vision.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiVisionResult(List<AiFood> foods) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiFood(List<AiCandidate> candidates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiCandidate(String name, double confidence) {
    }
}
