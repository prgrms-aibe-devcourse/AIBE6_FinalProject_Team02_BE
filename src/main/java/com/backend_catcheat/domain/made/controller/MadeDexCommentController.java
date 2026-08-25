package com.backend_catcheat.domain.made.controller;

import com.backend_catcheat.domain.made.dto.*;
import com.backend_catcheat.domain.made.service.MadeDexCommentService;
import com.backend_catcheat.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "로그잇 · 댓글", description = """
        기록에 남기는 댓글과 댓글 좋아요.
        댓글이 달리면 기록 작성자에게 알림이 가는데, **알림 전송이 실패해도 댓글은 남는다**(커밋 이후 이벤트로 분리).
        """)
@RestController
@RequestMapping("/api/v1/made-dexes/{madeDexId}/records/{recordId}/comments")
@RequiredArgsConstructor
public class MadeDexCommentController {

    private final MadeDexCommentService madeDexCommentService;

    @Operation(summary = "댓글 좋아요 토글", description = "누르면 켜지고 다시 누르면 꺼진다.")
    @PostMapping("/{commentId}/like")
    public ApiResponse<MadeDexCommentLikeResponseDTO> toggleLike(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long commentId
    ) {
        return ApiResponse.ok(madeDexCommentService.toggleLike(userId, commentId));
    }

    @Operation(summary = "댓글 작성", description = "작성이 커밋된 뒤에 기록 작성자에게 알림 이벤트가 발행된다.")
    @PostMapping
    public ApiResponse<MadeDexCommentCreateResponseDTO> create(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recordId,
            @RequestBody MadeDexCommentCreateRequestDTO request
    ) {
        return ApiResponse.ok(madeDexCommentService.create(userId, recordId, request));
    }

    @Operation(summary = "댓글 목록 조회", description = "기록에 달린 댓글을 좋아요 수·내가 눌렀는지와 함께 돌려준다.")
    @GetMapping
    public ApiResponse<List<MadeDexCommentDTO>> list(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recordId
    ) {

        return ApiResponse.ok(madeDexCommentService.findByRecord(userId, recordId));

    }

    @Operation(summary = "댓글 수정", description = "작성자만 가능.")
    @PutMapping("/{commentId}")
    public ApiResponse<Void> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long commentId,
            @RequestBody MadeDexCommentUpdateRequestDTO request
            ) {

        madeDexCommentService.update(userId, commentId, request);

        return ApiResponse.ok();
    }

    @Operation(summary = "댓글 삭제", description = "작성자만 가능.")
    @DeleteMapping("/{commentId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long commentId
            ) {

        madeDexCommentService.delete(userId, commentId);

        return ApiResponse.ok();

    }

}
