package com.backend_catcheat.domain.friend.controller;

import com.backend_catcheat.domain.friend.dto.FriendRequestCreateRequestDTO;
import com.backend_catcheat.domain.friend.dto.ReceivedRequestDTO;
import com.backend_catcheat.domain.friend.service.FriendService;
import com.backend_catcheat.domain.user.dto.UserBriefDTO;
import com.backend_catcheat.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "친구", description = """
        친구 요청과 목록. 친구 관계는 **방향이 없다** — A→B 와 B→A 가 같은 관계다.
        그래서 유일성은 방향성 제약이 아니라 정렬쌍 함수 인덱스로 지킨다(동시에 서로 요청해도 관계는 하나).
        """)
@RestController
@RequestMapping("/api/v1/friends")
@RequiredArgsConstructor
public class FriendController {
    private final FriendService friendService;

    /** 친구 요청 보내기 */
    @Operation(summary = "친구 요청 보내기", description = "상대에게 요청 알림이 간다. 이미 친구이거나 요청이 오가는 중이면 거절된다.")
    @PostMapping("/requests")
    public ApiResponse<Void> sendRequest(
            @AuthenticationPrincipal Long userId,
            @RequestBody FriendRequestCreateRequestDTO req
    ) {
        friendService.sendRequest(userId, req.targetUserId());
        return ApiResponse.ok();
    }

    /** 받은/보낸 요청 목록 */
    @Operation(summary = "친구 요청 목록", description = "`type=received`(기본) 는 받은 요청, `type=sent` 는 보낸 요청.")
    @GetMapping("/requests")
    public ApiResponse<List<ReceivedRequestDTO>> requests(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "received") String type
    ) {
        return ApiResponse.ok(friendService.listRequests(userId, !"sent".equalsIgnoreCase(type)));
    }

    /** 요청 수락 */
    @Operation(summary = "친구 요청 수락", description = "수신자만 가능. 수락하는 순간 양방향 친구 관계가 만들어진다.")
    @PostMapping("/requests/{requestId}/accept")
    public ApiResponse<Void> accept(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long requestId
    ) {
        friendService.accept(userId, requestId);
        return ApiResponse.ok();
    }

    /** 요청 거절(수신자) 또는 취소(요청자) */
    @Operation(summary = "친구 요청 거절 · 취소", description = "수신자가 부르면 거절, 요청자가 부르면 취소다.")
    @DeleteMapping("/requests/{requestId}")
    public ApiResponse<Void> deleteRequest(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long requestId
    ) {
        friendService.deleteRequest(userId, requestId);
        return ApiResponse.ok();
    }

    /** 내 친구 목록 */
    @Operation(summary = "내 친구 목록", description = "대표 뱃지까지 한 번에 묶어 내려간다(목록 조회에서 N+1 이 나지 않게 배치로 읽는다).")
    @GetMapping
    public ApiResponse<List<UserBriefDTO>> friends(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.ok(friendService.listFriends(userId));
    }

    /** 친구 삭제 */
    @Operation(summary = "친구 삭제", description = "관계에 방향이 없으므로 어느 쪽이 지워도 함께 끊긴다.")
    @DeleteMapping("/{otherUserId}")
    public ApiResponse<Void> removeFriend(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long otherUserId
    ) {
        friendService.removeFriend(userId, otherUserId);
        return ApiResponse.ok();
    }
}
