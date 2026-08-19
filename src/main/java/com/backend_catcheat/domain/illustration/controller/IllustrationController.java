package com.backend_catcheat.domain.illustration.controller;

import com.backend_catcheat.domain.illustration.dto.IllustrationCreateRequestDTO;
import com.backend_catcheat.domain.illustration.dto.IllustrationJobDTO;
import com.backend_catcheat.domain.illustration.dto.RevisionRequestDTO;
import com.backend_catcheat.domain.illustration.entity.RevisionPreset;
import com.backend_catcheat.domain.illustration.service.IllustrationService;
import com.backend_catcheat.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

// 생성이 20초 안팎 걸려 POST는 작업만 만들고 상태는 조회로 확인한다
@RestController
@RequestMapping("/api/v1/illustrations")
@RequiredArgsConstructor
public class IllustrationController {

    private final IllustrationService illustrationService;

    @PostMapping
    public ApiResponse<IllustrationJobDTO> create(
            @AuthenticationPrincipal Long userId,
            @RequestBody IllustrationCreateRequestDTO request) {
        return ApiResponse.ok(illustrationService.create(userId, request));
    }

    @PostMapping("/{jobId}/revisions")
    public ApiResponse<IllustrationJobDTO> revise(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long jobId,
            @RequestBody RevisionRequestDTO request) {
        return ApiResponse.ok(illustrationService.revise(userId, jobId, request));
    }

    @GetMapping("/{jobId}")
    public ApiResponse<IllustrationJobDTO> find(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long jobId) {
        return ApiResponse.ok(illustrationService.find(userId, jobId));
    }

    // 지시 문구는 서버가 들고 있으므로 라벨만 내린다
    @GetMapping("/revision-presets")
    public ApiResponse<List<PresetDTO>> presets() {
        return ApiResponse.ok(Arrays.stream(RevisionPreset.values())
                .map(preset -> new PresetDTO(preset.name(), preset.label()))
                .toList());
    }

    public record PresetDTO(String code, String label) {
    }
}
