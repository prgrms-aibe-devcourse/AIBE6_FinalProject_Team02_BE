package com.backend_catcheat.domain.my.controller;

import com.backend_catcheat.domain.my.dto.EquipBadgeRequest;
import com.backend_catcheat.domain.my.dto.MyBadgeResponse;
import com.backend_catcheat.domain.my.service.MyBadgeService;
import com.backend_catcheat.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "마이페이지 · 뱃지", description = """
        획득한 뱃지 보관함과 대표 뱃지 장착. 대표 뱃지는 닉네임 옆에 붙어 친구 목록·공개 프로필에서도 보인다.
        뱃지 지급은 가입·완주 같은 핵심 행동이 커밋된 뒤에 이벤트로 돈다 — 지급이 실패해도 그 행동은 되돌아가지 않는다.
        """)
@RestController
@RequestMapping("/api/v1/my/badges")
@RequiredArgsConstructor
public class MyBadgeController {
    private final MyBadgeService myBadgeService;

    /** 내 뱃지 보관함 (획득 목록 + 장착 표시) */
    @Operation(summary = "내 뱃지 보관함", description = "획득한 뱃지 목록과 현재 장착 여부.")
    @GetMapping
    public ApiResponse<List<MyBadgeResponse>> getMyBadges(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(myBadgeService.getMyBadges(userId));
    }

    /** 대표 뱃지 장착/해제 (badgeId null이면 해제). */
    @Operation(summary = "대표 뱃지 장착 · 해제", description = "`badgeId` 를 비우면 해제다. 보유하지 않은 뱃지는 장착할 수 없다.")
    @PatchMapping("/equip")
    public ApiResponse<Void> equip(
            @AuthenticationPrincipal Long userId,
            @RequestBody EquipBadgeRequest request) {
        myBadgeService.equip(userId, request.badgeId());
        return ApiResponse.ok();
    }
}
