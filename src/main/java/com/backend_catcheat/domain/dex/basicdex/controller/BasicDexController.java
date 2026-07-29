package com.backend_catcheat.domain.dex.basicdex.controller;

import com.backend_catcheat.domain.dex.basicdex.service.BasicDexService;
import com.backend_catcheat.domain.my.dto.MyBasicDexDetailResponseDTO;
import com.backend_catcheat.domain.my.dto.MyBasicDexResponseDTO;
import com.backend_catcheat.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dex")
@RequiredArgsConstructor
public class BasicDexController {
    private final BasicDexService basicDexService;

    @GetMapping("/me/basic")
    public ApiResponse<List<MyBasicDexResponseDTO>> getMyBasicDex(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(basicDexService.findMyBasicDex(userId));
    }

    @GetMapping("/me/basic/{slotId}")
    public ApiResponse<MyBasicDexDetailResponseDTO> getMyBasicDexDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long slotId) {
        return ApiResponse.ok(basicDexService.findMyBasicDexDetail(userId, slotId));
    }
}
