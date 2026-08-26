package com.backend_catcheat.domain.memo.controller;

import com.backend_catcheat.domain.memo.dto.MemoTemplateResponse;
import com.backend_catcheat.domain.memo.dto.MemoTemplateSaveRequest;
import com.backend_catcheat.domain.memo.service.MemoTemplateService;
import com.backend_catcheat.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "메모 템플릿", description = """
        기록에 자주 쓰는 문구를 저장해 두고 불러 쓴다. **최근 사용순**으로 내려가 자주 쓰는 것이 위로 올라온다.
        소유자는 인증 컨텍스트에서만 꺼낸다 — 요청 값의 userId 는 믿지 않는다.
        """)
@RestController
@RequestMapping("/api/v1/memo-templates")
@RequiredArgsConstructor
public class MemoTemplateController {

    private final MemoTemplateService memoTemplateService;

    /** 최근 사용순 */
    @Operation(summary = "내 메모 템플릿 목록", description = "**최근 사용순.** 방금 쓴 것이 맨 위로 온다.")
    @GetMapping
    public ApiResponse<List<MemoTemplateResponse>> findMine(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(memoTemplateService.findMine(userId));
    }

    @Operation(summary = "메모 템플릿 저장", description = "새 문구를 보관함에 넣는다.")
    @PostMapping
    public ApiResponse<MemoTemplateResponse> save(
            @AuthenticationPrincipal Long userId,
            @RequestBody MemoTemplateSaveRequest request) {
        return ApiResponse.ok(memoTemplateService.save(userId, request.content()));
    }

    /** 불러 쓴 것을 기록해 다음 조회에서 맨 위로 올린다 */
    @Operation(summary = "사용 기록 남기기", description = "불러 쓴 것을 기록해 다음 조회에서 맨 위로 올린다.")
    @PostMapping("/{templateId}/usages")
    public ApiResponse<Void> markUsed(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long templateId) {
        memoTemplateService.markUsed(userId, templateId);
        return ApiResponse.ok();
    }

    @Operation(summary = "메모 템플릿 삭제", description = "내 템플릿만 지울 수 있다.")
    @DeleteMapping("/{templateId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long templateId) {
        memoTemplateService.delete(userId, templateId);
        return ApiResponse.ok();
    }
}
