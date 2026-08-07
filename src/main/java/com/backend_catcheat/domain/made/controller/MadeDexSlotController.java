package com.backend_catcheat.domain.made.controller;

import com.backend_catcheat.domain.made.dto.MadeDexSlotCreateRequestDTO;
import com.backend_catcheat.domain.made.dto.MadeDexSlotDTO;
import com.backend_catcheat.domain.made.dto.MadeDexSlotDeleteResponseDTO;
import com.backend_catcheat.domain.made.dto.MadeDexSlotRenameRequestDTO;
import com.backend_catcheat.domain.made.dto.MadeDexSlotReorderRequestDTO;
import com.backend_catcheat.domain.made.service.MadeDexSlotService;
import com.backend_catcheat.global.common.ApiResponse;
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

@RestController
@RequestMapping("/api/v1/made-dexes/{madeDexId}/slots")
@RequiredArgsConstructor
public class MadeDexSlotController {

    private final MadeDexSlotService madeDexSlotService;

    /** 숨긴 슬롯까지 내려간다. 편집 화면이 되살리기를 그려야 한다 */
    @GetMapping
    public ApiResponse<List<MadeDexSlotDTO>> findSlots(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId) {
        return ApiResponse.ok(madeDexSlotService.findSlots(userId, madeDexId));
    }

    @PostMapping
    public ApiResponse<MadeDexSlotDTO> add(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId,
            @RequestBody MadeDexSlotCreateRequestDTO request) {
        return ApiResponse.ok(madeDexSlotService.add(userId, madeDexId, request.name()));
    }

    /** 이름 변경. 멤버 전원 가능 */
    @PatchMapping("/{slotId}")
    public ApiResponse<MadeDexSlotDTO> rename(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId,
            @PathVariable Long slotId,
            @RequestBody MadeDexSlotRenameRequestDTO request) {
        return ApiResponse.ok(madeDexSlotService.rename(userId, madeDexId, slotId, request.name()));
    }

    /** 순서 변경. 보이는 슬롯 전체를 새 순서대로 보낸다 */
    @PutMapping("/order")
    public ApiResponse<List<MadeDexSlotDTO>> reorder(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId,
            @RequestBody MadeDexSlotReorderRequestDTO request) {
        return ApiResponse.ok(madeDexSlotService.reorder(userId, madeDexId, request.slotIds()));
    }

    /** 없애기. 기록이 있으면 숨김으로 처리되고 hidden=true로 답한다 */
    @DeleteMapping("/{slotId}")
    public ApiResponse<MadeDexSlotDeleteResponseDTO> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId,
            @PathVariable Long slotId) {
        return ApiResponse.ok(madeDexSlotService.delete(userId, madeDexId, slotId));
    }

    /** 숨긴 슬롯 되살리기 */
    @PatchMapping("/{slotId}/restore")
    public ApiResponse<MadeDexSlotDTO> restore(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId,
            @PathVariable Long slotId) {
        return ApiResponse.ok(madeDexSlotService.restore(userId, madeDexId, slotId));
    }
}
