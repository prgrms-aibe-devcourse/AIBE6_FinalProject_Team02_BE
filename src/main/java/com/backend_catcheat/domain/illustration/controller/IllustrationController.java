package com.backend_catcheat.domain.illustration.controller;

import com.backend_catcheat.domain.illustration.dto.IllustrationCreateRequestDTO;
import com.backend_catcheat.domain.illustration.dto.IllustrationJobDTO;
import com.backend_catcheat.domain.illustration.dto.RevisionRequestDTO;
import com.backend_catcheat.domain.illustration.entity.RevisionPreset;
import com.backend_catcheat.domain.illustration.service.IllustrationService;
import com.backend_catcheat.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "일러스트", description = """
        사진을 도감 일러스트로 바꾸는 AI 작업.
        생성이 20초 안팎 걸려 **POST는 작업만 만들고 결과는 조회로 확인**한다(폴링).
        """)
@RestController
@RequestMapping("/api/v1/illustrations")
@RequiredArgsConstructor
public class IllustrationController {

    private final IllustrationService illustrationService;

    @Operation(summary = "일러스트 생성 요청", description = "작업을 만들고 즉시 반환한다. 완료 여부는 작업 조회로 확인한다.")
    @PostMapping
    public ApiResponse<IllustrationJobDTO> create(
            @AuthenticationPrincipal Long userId,
            @RequestBody IllustrationCreateRequestDTO request) {
        return ApiResponse.ok(illustrationService.create(userId, request));
    }

    @Operation(summary = "일러스트 수정 요청", description = "기존 작업 결과를 프리셋 지시로 다시 그린다. 지시 문구는 서버가 들고 있다.")
    @PostMapping("/{jobId}/revisions")
    public ApiResponse<IllustrationJobDTO> revise(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long jobId,
            @RequestBody RevisionRequestDTO request) {
        return ApiResponse.ok(illustrationService.revise(userId, jobId, request));
    }

    @Operation(summary = "일러스트 작업 조회", description = "작업 상태와 결과 이미지를 돌려준다. 생성 요청 뒤 이 API로 완료를 확인한다.")
    @GetMapping("/{jobId}")
    public ApiResponse<IllustrationJobDTO> find(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long jobId) {
        return ApiResponse.ok(illustrationService.find(userId, jobId));
    }

    // 지시 문구는 서버가 들고 있으므로 라벨만 내린다
    @Operation(summary = "수정 프리셋 목록", description = "선택 가능한 수정 지시의 코드와 라벨. 실제 프롬프트 문구는 서버에만 있다.")
    @GetMapping("/revision-presets")
    public ApiResponse<List<PresetDTO>> presets() {
        return ApiResponse.ok(Arrays.stream(RevisionPreset.values())
                .map(preset -> new PresetDTO(preset.name(), preset.label()))
                .toList());
    }

    public record PresetDTO(String code, String label) {
    }
}
