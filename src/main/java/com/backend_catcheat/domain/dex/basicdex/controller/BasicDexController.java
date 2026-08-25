package com.backend_catcheat.domain.dex.basicdex.controller;

import com.backend_catcheat.domain.dex.basicdex.service.BasicDexService;
import com.backend_catcheat.domain.my.dto.MyBasicDexDetailResponseDTO;
import com.backend_catcheat.domain.my.dto.MyBasicDexResponseDTO;
import com.backend_catcheat.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "베이짓 · 기본 도감", description = """
        운영진이 정한 **200칸 고정** 도감. 칸이 고정이라 수집률이 성립한다.
        수집률은 칸 기준이라 같은 음식을 다시 수집하면 별(rank)만 오르고 수집률은 그대로다(별 최대 3).
        """)
@RestController
@RequestMapping("/api/v1/dex")
@RequiredArgsConstructor
public class BasicDexController {
    private final BasicDexService basicDexService;

    @Operation(summary = "내 기본 도감 조회", description = "200칸 전체를 해금 여부와 함께 돌려준다. 카테고리별 수집률은 조회 시점에 계산한다.")
    @GetMapping("/me/basic")
    public ApiResponse<List<MyBasicDexResponseDTO>> getMyBasicDex(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(basicDexService.findMyBasicDex(userId));
    }

    @Operation(summary = "도감 칸 상세 조회", description = "한 칸의 해금 이력과 기록을 돌려준다. 아직 해금하지 않은 칸도 조회할 수 있다.")
    @GetMapping("/me/basic/{slotId}")
    public ApiResponse<MyBasicDexDetailResponseDTO> getMyBasicDexDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long slotId) {
        return ApiResponse.ok(basicDexService.findMyBasicDexDetail(userId, slotId));
    }

    /** New 스티커 확인 처리 */
    @Operation(summary = "New 스티커 확인 처리", description = "새로 해금한 칸에 붙는 New 스티커를 확인한 것으로 남긴다.")
    @PatchMapping("/me/basic/{slotId}/new-badge-seen")
    public ApiResponse<Void> markNewBadgeSeen(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long slotId) {
        basicDexService.markNewBadgeSeen(userId, slotId);
        return ApiResponse.ok();
    }
}
