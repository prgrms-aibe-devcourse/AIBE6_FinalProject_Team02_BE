package com.backend_catcheat.domain.made.controller;

import com.backend_catcheat.domain.made.dto.*;
import com.backend_catcheat.domain.made.service.MadeDexCommentService;
import com.backend_catcheat.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/made-dexes/{madeDexId}/records/{recordId}/comments")
@RequiredArgsConstructor
public class MadeDexCommentController {

    private final MadeDexCommentService madeDexCommentService;

    @PostMapping("/{commentId}/like")
    public ApiResponse<MadeDexCommentLikeResponseDTO> toggleLike(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long commentId
    ) {
        return ApiResponse.ok(madeDexCommentService.toggleLike(userId, commentId));
    }

    @PostMapping
    public ApiResponse<MadeDexCommentCreateResponseDTO> create(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recordId,
            @RequestBody MadeDexCommentCreateRequestDTO request
    ) {
        return ApiResponse.ok(madeDexCommentService.create(userId, recordId, request));
    }

    @GetMapping
    public ApiResponse<List<MadeDexCommentDTO>> list(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recordId
    ) {

        return ApiResponse.ok(madeDexCommentService.findByRecord(userId, recordId));

    }

    @PutMapping("/{commentId}")
    public ApiResponse<Void> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long commentId,
            @RequestBody MadeDexCommentUpdateRequestDTO request
            ) {

        madeDexCommentService.update(userId, commentId, request);

        return ApiResponse.ok();
    }

    @DeleteMapping("/{commentId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long commentId
            ) {

        madeDexCommentService.delete(userId, commentId);

        return ApiResponse.ok();

    }

}
