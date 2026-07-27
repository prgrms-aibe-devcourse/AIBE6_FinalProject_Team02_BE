package com.backend_catcheat.domain.my.controller;

import com.backend_catcheat.domain.my.dto.MyProfileResponse;
import com.backend_catcheat.domain.my.dto.NicknameRequest;
import com.backend_catcheat.domain.my.service.MyService;
import com.backend_catcheat.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/my")
@RequiredArgsConstructor
public class MyController {
    private final MyService myService;

    /** 마이페이지 프로필 조회 (닉네임 + 변경 가능 여부/가능 시각) */
    @GetMapping("/profile")
    public ApiResponse<MyProfileResponse> getProfile(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(myService.getProfile(userId));
    }

    /**
     * 최초 닉네임 세팅 (온보딩 전)
     */
    @PostMapping("/nickname")
    public ApiResponse<Void> setInitialNickname(
            @AuthenticationPrincipal Long userId,
            @RequestBody NicknameRequest request) {
        myService.setInitialNickname(userId, request.nickname());
        return ApiResponse.ok();
    }

    /** 닉네임 변경 (마이페이지)
     * 마지막 변경 후 1개월이 지나야 가능 */
    @PatchMapping("/nickname")
    public ApiResponse<Void> changeNickname(
            @AuthenticationPrincipal Long userId,
            @RequestBody NicknameRequest request) {
        myService.changeNickname(userId, request.nickname());
        return ApiResponse.ok();
    }

    /** 회원 탈퇴 (소프트 삭제) */
    @DeleteMapping
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal Long userId) {
        myService.withdraw(userId);
        return ApiResponse.ok();
    }
}
