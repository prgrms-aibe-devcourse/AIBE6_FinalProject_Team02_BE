package com.backend_catcheat.domain.made.controller;

import com.backend_catcheat.domain.made.dto.MadeDexCreateRequestDTO;
import com.backend_catcheat.domain.made.dto.MadeDexCreateResponseDTO;
import com.backend_catcheat.domain.made.dto.MadeDexDetailDTO;
import com.backend_catcheat.domain.made.dto.MadeDexInvitePreviewDTO;
import com.backend_catcheat.domain.made.dto.MadeDexInviteResponseDTO;
import com.backend_catcheat.domain.made.dto.MadeDexJoinRequestDTO;
import com.backend_catcheat.domain.made.dto.MadeDexJoinResponseDTO;
import com.backend_catcheat.domain.made.dto.MadeDexLeaveResponseDTO;
import com.backend_catcheat.domain.made.dto.MadeDexMembersResponseDTO;
import com.backend_catcheat.domain.made.dto.MadeDexOwnerTransferRequestDTO;
import com.backend_catcheat.domain.made.dto.MadeDexSummaryDTO;
import com.backend_catcheat.domain.made.dto.MadeDexUpdateRequestDTO;
import com.backend_catcheat.domain.made.service.MadeDexInviteService;
import com.backend_catcheat.domain.made.service.MadeDexMemberService;
import com.backend_catcheat.domain.made.service.MadeDexService;
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
@RequestMapping("/api/v1/made-dexes")
@RequiredArgsConstructor
public class MadeDexController {

    private final MadeDexService madeDexService;
    private final MadeDexInviteService madeDexInviteService;
    private final MadeDexMemberService madeDexMemberService;

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

    /** 도감 상세. 공개 도감은 참여하지 않아도 볼 수 있다 */
    @GetMapping("/{madeDexId}")
    public ApiResponse<MadeDexDetailDTO> detail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId) {
        return ApiResponse.ok(madeDexService.findDetail(userId, madeDexId));
    }

    /** 도감 정보 수정. 그룹장만. 보낸 값으로 전부 교체된다 */
    @PutMapping("/{madeDexId}")
    public ApiResponse<Void> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId,
            @RequestBody MadeDexUpdateRequestDTO request) {
        madeDexService.update(userId, madeDexId, request);
        return ApiResponse.ok();
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

    /** 참여자 목록. 멤버만 볼 수 있다 */
    @GetMapping("/{madeDexId}/members")
    public ApiResponse<MadeDexMembersResponseDTO> members(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId) {
        return ApiResponse.ok(madeDexMemberService.findMembers(userId, madeDexId));
    }

    /** 추방. 그룹장만 */
    @DeleteMapping("/{madeDexId}/members/{targetUserId}")
    public ApiResponse<Void> kick(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId,
            @PathVariable Long targetUserId) {
        madeDexMemberService.kick(userId, madeDexId, targetUserId);
        return ApiResponse.ok();
    }

    /** 자유 탈퇴. 그룹장이 나가면 그룹이 삭제된다 */
    @DeleteMapping("/{madeDexId}/members/me")
    public ApiResponse<MadeDexLeaveResponseDTO> leave(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId) {
        return ApiResponse.ok(madeDexMemberService.leave(userId, madeDexId));
    }

    /** 그룹장 위임. 넘긴 사람은 일반 멤버가 된다 */
    @PatchMapping("/{madeDexId}/owner")
    public ApiResponse<Void> transferOwner(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId,
            @RequestBody MadeDexOwnerTransferRequestDTO request) {
        madeDexMemberService.transferOwner(userId, madeDexId, request.userId());
        return ApiResponse.ok();
    }
}
