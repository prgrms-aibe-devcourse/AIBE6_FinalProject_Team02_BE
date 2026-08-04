package com.backend_catcheat.domain.made.controller;

import com.backend_catcheat.domain.made.dto.MadeDexCreateRequestDTO;
import com.backend_catcheat.domain.made.dto.MadeDexCreateResponseDTO;
import com.backend_catcheat.domain.made.dto.MadeDexInvitePreviewDTO;
import com.backend_catcheat.domain.made.dto.MadeDexInviteResponseDTO;
import com.backend_catcheat.domain.made.dto.MadeDexJoinRequestDTO;
import com.backend_catcheat.domain.made.dto.MadeDexJoinResponseDTO;
import com.backend_catcheat.domain.made.dto.MadeDexSummaryDTO;
import com.backend_catcheat.domain.made.service.MadeDexInviteService;
import com.backend_catcheat.domain.made.service.MadeDexService;
import com.backend_catcheat.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final MadeDexInviteService madeDexInviteService;

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

    /** 초대 코드 발급/재발급. 그룹장만. 이전 코드는 이 시점에 무효화된다 */
    @PostMapping("/{madeDexId}/invites")
    public ApiResponse<MadeDexInviteResponseDTO> issueInvite(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId) {
        return ApiResponse.ok(madeDexInviteService.issue(userId, madeDexId));
    }

    /** 현재 유효한 코드. 없으면 data가 null이다 */
    @GetMapping("/{madeDexId}/invites/active")
    public ApiResponse<MadeDexInviteResponseDTO> activeInvite(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId) {
        return ApiResponse.ok(madeDexInviteService.findActive(userId, madeDexId));
    }

    /** 코드/링크로 들어온 사람이 참여 전에 보는 그룹 정보 */
    @GetMapping("/invites/{code}")
    public ApiResponse<MadeDexInvitePreviewDTO> invitePreview(
            @AuthenticationPrincipal Long userId,
            @PathVariable String code) {
        return ApiResponse.ok(madeDexInviteService.preview(userId, code));
    }

    /** 코드로 참여. 그룹 id는 코드가 알려주므로 경로에 두지 않는다 */
    @PostMapping("/join")
    public ApiResponse<MadeDexJoinResponseDTO> join(
            @AuthenticationPrincipal Long userId,
            @RequestBody MadeDexJoinRequestDTO request) {
        return ApiResponse.ok(madeDexInviteService.join(userId, request.code()));
    }
}
