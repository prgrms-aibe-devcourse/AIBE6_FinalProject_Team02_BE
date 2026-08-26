package com.backend_catcheat.domain.made.controller;

import com.backend_catcheat.domain.made.dto.MadeDexRecordCreateRequestDTO;
import com.backend_catcheat.domain.made.dto.MadeDexRecordCreateResponseDTO;
import com.backend_catcheat.domain.made.dto.MadeDexRecordDetailDTO;
import com.backend_catcheat.domain.made.dto.MadeDexRecordLikeResponseDTO;
import com.backend_catcheat.domain.made.dto.MadeDexRecordUpdateRequestDTO;
import com.backend_catcheat.domain.made.service.MadeDexRecordService;
import com.backend_catcheat.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "로그잇 · 기록", description = """
        기록의 단위는 음식이 아니라 **사람**이다. 같은 음식을 나눠 먹어도 각자의 기록이 따로 남는다.

        **한 사람이 한 끼니에 남기는 건 하루 한 건**이다(도감 × 끼니 × 사람 × 날짜).
        서비스 선검사와 DB 부분 유니크 인덱스가 이 규칙을 함께 지킨다 — 동시 요청은 409 로 막힌다.
        """)
@RestController
@RequestMapping("/api/v1/made-dexes/{madeDexId}/records")
@RequiredArgsConstructor
public class MadeDexRecordController {

    private final MadeDexRecordService madeDexRecordService;

    @Operation(summary = "기록 등록", description = """
            오늘 날짜로만 남길 수 있다. 이미 그 끼니에 내 기록이 있으면 409(이미 기록한 끼니)다.
            지운 기록은 자리를 비켜 주므로 삭제 후 다시 등록할 수 있다.
            """)
    @PostMapping
    public ApiResponse<MadeDexRecordCreateResponseDTO> create(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId,
            @RequestBody MadeDexRecordCreateRequestDTO request) {
        return ApiResponse.ok(madeDexRecordService.create(userId, madeDexId, request));
    }

    /** 수정. 작성자만. 사진·음식명은 보낸 목록으로 전부 교체된다 */
    @Operation(summary = "기록 수정", description = """
            작성자만 가능. 사진·음식명은 **보낸 목록으로 전부 교체**된다.
            지난 날짜의 기록은 사진에 붙인 글만 고칠 수 있고, 끼니를 옮기거나 사진을 더하는 것은 막힌다.
            """)
    @PutMapping("/{recordId}")
    public ApiResponse<Void> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId,
            @PathVariable Long recordId,
            @RequestBody MadeDexRecordUpdateRequestDTO request) {
        madeDexRecordService.update(userId, madeDexId, recordId, request);
        return ApiResponse.ok();
    }

    /** 삭제. 작성자만. 그룹장도 남의 기록은 지우지 못한다 */
    @Operation(summary = "기록 삭제", description = "작성자만 가능. **그룹장도 남의 기록은 지우지 못한다.** 소프트 삭제라 그 끼니 자리는 다시 열린다.")
    @DeleteMapping("/{recordId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId,
            @PathVariable Long recordId) {
        madeDexRecordService.delete(userId, madeDexId, recordId);
        return ApiResponse.ok();
    }

    /** 기록 상세. 공개 그룹은 참여하지 않아도 볼 수 있다 */
    @Operation(summary = "기록 상세 조회", description = "공개 그룹은 참여하지 않아도 볼 수 있다.")
    @GetMapping("/{recordId}")
    public ApiResponse<MadeDexRecordDetailDTO> detail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId,
            @PathVariable Long recordId) {
        return ApiResponse.ok(madeDexRecordService.findDetail(userId, madeDexId, recordId));
    }

    @Operation(summary = "기록 좋아요 토글", description = "누르면 켜지고 다시 누르면 꺼진다. 응답에 현재 상태와 개수가 담긴다.")
    @PostMapping("/{recordId}/like")
    public ApiResponse<MadeDexRecordLikeResponseDTO> toggleLike(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId,
            @PathVariable Long recordId) {
        return ApiResponse.ok(madeDexRecordService.toggleLike(userId, madeDexId, recordId));
    }
}
