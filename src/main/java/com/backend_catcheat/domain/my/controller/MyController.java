package com.backend_catcheat.domain.my.controller;

import com.backend_catcheat.domain.my.dto.NicknameRequest;
import com.backend_catcheat.domain.my.service.MyService;
import com.backend_catcheat.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/my")
@RequiredArgsConstructor
public class MyController {
    private final MyService myService;

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
}
