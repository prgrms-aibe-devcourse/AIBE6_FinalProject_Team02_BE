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

@Tag(name = "로그잇 · 도감", description = """
        여러 명이 함께 쓰는 공동 기록 도감. 그룹 생성·초대·멤버 관리를 담당한다.

        초대 코드는 **그룹당 유효한 것 하나**뿐이라 재발급하면 이전 코드가 그 자리에서 죽는다.
        참여는 그룹 행을 잠근 뒤 정원을 세므로, 마지막 한 자리를 두 명이 동시에 얻지 못한다.
        """)
@RestController
@RequestMapping("/api/v1/made-dexes")
@RequiredArgsConstructor
public class MadeDexController {

    private final MadeDexService madeDexService;
    private final MadeDexInviteService madeDexInviteService;
    private final MadeDexMemberService madeDexMemberService;

    @Operation(summary = "도감 생성", description = "만든 사람이 그룹장이 된다. 끼니 슬롯은 기본값(아침·점심·저녁)으로 함께 만들어진다.")
    @PostMapping
    public ApiResponse<MadeDexCreateResponseDTO> create(
            @AuthenticationPrincipal Long userId,
            @RequestBody MadeDexCreateRequestDTO request) {
        return ApiResponse.ok(madeDexService.create(userId, request));
    }

    /** 내가 속한 그룹만. 가입 개수 제한은 없다 */
    @Operation(summary = "내 도감 목록", description = "내가 속한 그룹만 내려간다. 가입할 수 있는 그룹 개수에는 제한이 없다.")
    @GetMapping
    public ApiResponse<List<MadeDexSummaryDTO>> findMine(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(madeDexService.findMine(userId));
    }

    /** 도감 상세. 공개 도감은 참여하지 않아도 볼 수 있다 */
    @Operation(summary = "도감 상세 조회", description = "공개 도감은 참여하지 않아도 볼 수 있다.")
    @GetMapping("/{madeDexId}")
    public ApiResponse<MadeDexDetailDTO> detail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId) {
        return ApiResponse.ok(madeDexService.findDetail(userId, madeDexId));
    }

    /** 도감 정보 수정. 그룹장만. 보낸 값으로 전부 교체된다 */
    @Operation(summary = "도감 정보 수정", description = "그룹장만 가능. 부분 수정이 아니라 **보낸 값으로 전부 교체**된다.")
    @PutMapping("/{madeDexId}")
    public ApiResponse<Void> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId,
            @RequestBody MadeDexUpdateRequestDTO request) {
        madeDexService.update(userId, madeDexId, request);
        return ApiResponse.ok();
    }

    /** 초대 코드 발급/재발급. 그룹장만. 이전 코드는 이 시점에 무효화된다 */
    @Operation(summary = "초대 코드 발급 · 재발급", description = """
            그룹장만 가능. **이전 코드는 이 시점에 즉시 무효화**된다(그룹당 유효 코드 1개).
            코드는 7일간 유효하고, 그동안 여러 명이 같은 코드로 들어올 수 있다.
            """)
    @PostMapping("/{madeDexId}/invites")
    public ApiResponse<MadeDexInviteResponseDTO> issueInvite(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId) {
        return ApiResponse.ok(madeDexInviteService.issue(userId, madeDexId));
    }

    /** 현재 유효한 코드. 없으면 data가 null이다 */
    @Operation(summary = "현재 유효한 초대 코드 조회", description = "아직 한 번도 안 뽑았거나 만료됐으면 `data` 가 null 이다.")
    @GetMapping("/{madeDexId}/invites/active")
    public ApiResponse<MadeDexInviteResponseDTO> activeInvite(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId) {
        return ApiResponse.ok(madeDexInviteService.findActive(userId, madeDexId));
    }

    /** 코드/링크로 들어온 사람이 참여 전에 보는 그룹 정보 */
    @Operation(summary = "초대 코드 미리보기", description = "코드·링크로 들어온 사람이 참여 전에 보는 그룹 정보(이름·인원·정원·이미 멤버인지).")
    @GetMapping("/invites/{code}")
    public ApiResponse<MadeDexInvitePreviewDTO> invitePreview(
            @AuthenticationPrincipal Long userId,
            @PathVariable String code) {
        return ApiResponse.ok(madeDexInviteService.preview(userId, code));
    }

    /** 코드로 참여. 그룹 id는 코드가 알려주므로 경로에 두지 않는다 */
    @Operation(summary = "초대 코드로 참여", description = """
            그룹 id 는 코드가 알려주므로 경로에 두지 않는다.
            그룹 행을 잠근 뒤 코드 유효성·중복 참여·정원을 다시 확인하고 넣는다.
            """)
    @PostMapping("/join")
    public ApiResponse<MadeDexJoinResponseDTO> join(
            @AuthenticationPrincipal Long userId,
            @RequestBody MadeDexJoinRequestDTO request) {
        return ApiResponse.ok(madeDexInviteService.join(userId, request.code()));
    }

    /** 참여자 목록. 멤버만 볼 수 있다 */
    @Operation(summary = "참여자 목록", description = "멤버만 볼 수 있다.")
    @GetMapping("/{madeDexId}/members")
    public ApiResponse<MadeDexMembersResponseDTO> members(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId) {
        return ApiResponse.ok(madeDexMemberService.findMembers(userId, madeDexId));
    }

    /** 추방. 그룹장만 */
    @Operation(summary = "멤버 추방", description = "그룹장만 가능.")
    @DeleteMapping("/{madeDexId}/members/{targetUserId}")
    public ApiResponse<Void> kick(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId,
            @PathVariable Long targetUserId) {
        madeDexMemberService.kick(userId, madeDexId, targetUserId);
        return ApiResponse.ok();
    }

    /** 자유 탈퇴. 그룹장이 나가면 그룹이 삭제된다 */
    @Operation(summary = "그룹 나가기", description = "누구나 나갈 수 있다. **그룹장이 나가면 그룹 자체가 삭제**된다.")
    @DeleteMapping("/{madeDexId}/members/me")
    public ApiResponse<MadeDexLeaveResponseDTO> leave(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId) {
        return ApiResponse.ok(madeDexMemberService.leave(userId, madeDexId));
    }

    /** 그룹장 위임. 넘긴 사람은 일반 멤버가 된다 */
    @Operation(summary = "그룹장 위임", description = "넘긴 사람은 일반 멤버가 된다.")
    @PatchMapping("/{madeDexId}/owner")
    public ApiResponse<Void> transferOwner(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long madeDexId,
            @RequestBody MadeDexOwnerTransferRequestDTO request) {
        madeDexMemberService.transferOwner(userId, madeDexId, request.userId());
        return ApiResponse.ok();
    }
}
