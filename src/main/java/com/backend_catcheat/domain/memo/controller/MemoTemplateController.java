package com.backend_catcheat.domain.memo.controller;

import com.backend_catcheat.domain.memo.dto.MemoTemplateResponse;
import com.backend_catcheat.domain.memo.dto.MemoTemplateSaveRequest;
import com.backend_catcheat.domain.memo.service.MemoTemplateService;
import com.backend_catcheat.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** userId는 인증 컨텍스트에서만 꺼낸다 — 요청 값을 믿지 않는다 */
@RestController
@RequestMapping("/api/v1/memo-templates")
@RequiredArgsConstructor
public class MemoTemplateController {

    private final MemoTemplateService memoTemplateService;

    /** 최근 사용순 */
    @GetMapping
    public ApiResponse<List<MemoTemplateResponse>> findMine(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(memoTemplateService.findMine(userId));
    }

    @PostMapping
    public ApiResponse<MemoTemplateResponse> save(
            @AuthenticationPrincipal Long userId,
            @RequestBody MemoTemplateSaveRequest request) {
        return ApiResponse.ok(memoTemplateService.save(userId, request.content()));
    }

    /** 불러 쓴 것을 기록해 다음 조회에서 맨 위로 올린다 */
    @PostMapping("/{templateId}/usages")
    public ApiResponse<Void> markUsed(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long templateId) {
        memoTemplateService.markUsed(userId, templateId);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{templateId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long templateId) {
        memoTemplateService.delete(userId, templateId);
        return ApiResponse.ok();
    }
}
