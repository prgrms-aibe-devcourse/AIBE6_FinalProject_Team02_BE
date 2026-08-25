package com.backend_catcheat.domain.made.controller;

import com.backend_catcheat.domain.made.dto.MadeDexSlotCreateRequestDTO;
import com.backend_catcheat.domain.made.dto.MadeDexSlotDTO;
import com.backend_catcheat.domain.made.dto.MadeDexSlotDeleteResponseDTO;
import com.backend_catcheat.domain.made.dto.MadeDexSlotRenameRequestDTO;
import com.backend_catcheat.domain.made.dto.MadeDexSlotReorderRequestDTO;
import com.backend_catcheat.domain.made.service.MadeDexSlotService;
import com.backend_catcheat.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "로그잇 · 끼니 슬롯", description = """
        끼니(슬롯)는 **그룹마다 이름과 개수가 달라** 전역 enum이 아니라 테이블로 둔다. 기본값은 아침·점심·저녁, 1~6개.
        유저가 칸을 정의하는 도감이라 정답이 없고, 그래서 로그잇에는 AI 판별과 진행률이 없다.
        """)
@RestController
@RequestMapping("/api/v1/made-dexes/{madeDexId}/slots")
@RequiredArgsConstructor
public class MadeDexSlotController {

    private final MadeDexSlotService madeDexSlotService;

    /** 숨긴 슬롯까지 내려간다. 편집 화면이 되살리기를 그려야 한다 */
    @Operation(summary = "끼니 목록 조회", description = "**숨긴 슬롯까지** 내려간다 — 편집 화면이 되살리기를 그려야 하기 때문이다.")
    @GetMapping
    public ApiResponse<List<MadeDexSlotDTO>> findSlots(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId) {
        return ApiResponse.ok(madeDexSlotService.findSlots(userId, madeDexId));
    }

    @Operation(summary = "끼니 추가", description = "멤버 누구나 추가할 수 있다. 최대 6개.")
    @PostMapping
    public ApiResponse<MadeDexSlotDTO> add(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId,
            @RequestBody MadeDexSlotCreateRequestDTO request) {
        return ApiResponse.ok(madeDexSlotService.add(userId, madeDexId, request.name()));
    }

    /** 이름 변경. 멤버 전원 가능 */
    @Operation(summary = "끼니 이름 변경", description = "멤버 누구나 가능.")
    @PatchMapping("/{slotId}")
    public ApiResponse<MadeDexSlotDTO> rename(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId,
            @PathVariable Long slotId,
            @RequestBody MadeDexSlotRenameRequestDTO request) {
        return ApiResponse.ok(madeDexSlotService.rename(userId, madeDexId, slotId, request.name()));
    }

    /** 순서 변경. 보이는 슬롯 전체를 새 순서대로 보낸다 */
    @Operation(summary = "끼니 순서 변경", description = "일부가 아니라 **보이는 슬롯 전체**를 새 순서대로 보낸다.")
    @PutMapping("/order")
    public ApiResponse<List<MadeDexSlotDTO>> reorder(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId,
            @RequestBody MadeDexSlotReorderRequestDTO request) {
        return ApiResponse.ok(madeDexSlotService.reorder(userId, madeDexId, request.slotIds()));
    }

    /** 없애기. 기록이 있으면 숨김으로 처리되고 hidden=true로 답한다 */
    @Operation(summary = "끼니 없애기", description = """
            이미 기록이 붙어 있으면 지우지 않고 **숨김 처리**한다 — 지난 기록이 사라지면 안 되기 때문이다.
            그 경우 응답의 `hidden` 이 true 로 온다.
            """)
    @DeleteMapping("/{slotId}")
    public ApiResponse<MadeDexSlotDeleteResponseDTO> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId,
            @PathVariable Long slotId) {
        return ApiResponse.ok(madeDexSlotService.delete(userId, madeDexId, slotId));
    }

    /** 숨긴 슬롯 되살리기 */
    @Operation(summary = "숨긴 끼니 되살리기", description = "숨김 처리된 슬롯을 다시 보이게 한다.")
    @PatchMapping("/{slotId}/restore")
    public ApiResponse<MadeDexSlotDTO> restore(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId,
            @PathVariable Long slotId) {
        return ApiResponse.ok(madeDexSlotService.restore(userId, madeDexId, slotId));
    }
}
