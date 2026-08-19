package com.backend_catcheat.domain.made.controller;

import com.backend_catcheat.domain.made.dto.MadeDexRecordCreateRequestDTO;
import com.backend_catcheat.domain.made.dto.MadeDexRecordCreateResponseDTO;
import com.backend_catcheat.domain.made.dto.MadeDexRecordDetailDTO;
import com.backend_catcheat.domain.made.dto.MadeDexRecordLikeResponseDTO;
import com.backend_catcheat.domain.made.dto.MadeDexRecordUpdateRequestDTO;
import com.backend_catcheat.domain.made.service.MadeDexRecordService;
import com.backend_catcheat.global.common.ApiResponse;
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

@RestController
@RequestMapping("/api/v1/made-dexes/{madeDexId}/records")
@RequiredArgsConstructor
public class MadeDexRecordController {

    private final MadeDexRecordService madeDexRecordService;

    @PostMapping
    public ApiResponse<MadeDexRecordCreateResponseDTO> create(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId,
            @RequestBody MadeDexRecordCreateRequestDTO request) {
        return ApiResponse.ok(madeDexRecordService.create(userId, madeDexId, request));
    }

    /** 수정. 작성자만. 사진·음식명은 보낸 목록으로 전부 교체된다 */
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
    @DeleteMapping("/{recordId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId,
            @PathVariable Long recordId) {
        madeDexRecordService.delete(userId, madeDexId, recordId);
        return ApiResponse.ok();
    }

    /** 기록 상세. 공개 그룹은 참여하지 않아도 볼 수 있다 */
    @GetMapping("/{recordId}")
    public ApiResponse<MadeDexRecordDetailDTO> detail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId,
            @PathVariable Long recordId) {
        return ApiResponse.ok(madeDexRecordService.findDetail(userId, madeDexId, recordId));
    }

    @PostMapping("/{recordId}/like")
    public ApiResponse<MadeDexRecordLikeResponseDTO> toggleLike(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId,
            @PathVariable Long recordId) {
        return ApiResponse.ok(madeDexRecordService.toggleLike(userId, madeDexId, recordId));
    }
}
