package com.backend_catcheat.domain.illustration.dto;

import com.backend_catcheat.domain.illustration.entity.RevisionPreset;

import java.util.List;

public record RevisionRequestDTO(
        List<RevisionPreset> presets,
        String freeText
) {
}
