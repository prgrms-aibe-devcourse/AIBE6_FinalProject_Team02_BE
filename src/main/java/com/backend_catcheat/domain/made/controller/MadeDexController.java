package com.backend_catcheat.domain.made.controller;

import com.backend_catcheat.domain.made.dto.MadeDexCreateRequestDTO;
import com.backend_catcheat.domain.made.dto.MadeDexCreateResponseDTO;
import com.backend_catcheat.domain.made.dto.MadeDexSummaryDTO;
import com.backend_catcheat.domain.made.service.MadeDexService;
import com.backend_catcheat.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/made-dexes")
@RequiredArgsConstructor
public class MadeDexController {

    private final MadeDexService madeDexService;

    @PostMapping
    public ApiResponse<MadeDexCreateResponseDTO> create(
            @AuthenticationPrincipal Long userId,
            @RequestBody MadeDexCreateRequestDTO request) {
        return ApiResponse.ok(madeDexService.create(userId, request));
    }

    /** 내가 속한 그룹만. 가입 개수 제한은 없다 */
    @GetMapping
    public ApiResponse<List<MadeDexSummaryDTO>> findMine(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(madeDexService.findMine(userId));
    }
}
