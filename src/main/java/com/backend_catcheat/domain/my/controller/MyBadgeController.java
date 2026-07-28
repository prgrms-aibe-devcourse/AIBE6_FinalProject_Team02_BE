package com.backend_catcheat.domain.my.controller;

import com.backend_catcheat.domain.my.dto.EquipBadgeRequest;
import com.backend_catcheat.domain.my.dto.MyBadgeResponse;
import com.backend_catcheat.domain.my.service.MyBadgeService;
import com.backend_catcheat.global.common.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/my/badges")
@RequiredArgsConstructor
public class MyBadgeController {
    private final MyBadgeService myBadgeService;

    /** 내 뱃지 보관함 (획득 목록 + 장착 표시) */
    @GetMapping
    public ApiResponse<List<MyBadgeResponse>> getMyBadges(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(myBadgeService.getMyBadges(userId));
    }

    /** 대표 뱃지 장착/해제 (badgeId null이면 해제). */
    @PatchMapping("/equip")
    public ApiResponse<Void> equip(
            @AuthenticationPrincipal Long userId,
            @RequestBody EquipBadgeRequest request) {
        myBadgeService.equip(userId, request.badgeId());
        return ApiResponse.ok();
    }
}
