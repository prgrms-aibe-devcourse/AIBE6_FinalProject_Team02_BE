package com.backend_catcheat.domain.friend.controller;

import com.backend_catcheat.domain.friend.dto.FriendRequestCreateRequestDTO;
import com.backend_catcheat.domain.friend.dto.ReceivedRequestDTO;
import com.backend_catcheat.domain.friend.service.FriendService;
import com.backend_catcheat.domain.user.dto.UserBriefDTO;
import com.backend_catcheat.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/friends")
@RequiredArgsConstructor
public class FriendController {
    private final FriendService friendService;

    /** 친구 요청 보내기 */
    @PostMapping("/requests")
    public ApiResponse<Void> sendRequest(
            @AuthenticationPrincipal Long userId,
            @RequestBody FriendRequestCreateRequestDTO req
    ) {
        friendService.sendRequest(userId, req.targetUserId());
        return ApiResponse.ok();
    }

    /** 받은/보낸 요청 목록 */
    @GetMapping("/requests")
    public ApiResponse<List<ReceivedRequestDTO>> requests(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "received") String type
    ) {
        return ApiResponse.ok(friendService.listRequests(userId, !"sent".equalsIgnoreCase(type)));
    }

    /** 요청 수락 */
    @PostMapping("/requests/{requestId}/accept")
    public ApiResponse<Void> accept(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long requestId
    ) {
        friendService.accept(userId, requestId);
        return ApiResponse.ok();
    }

    /** 요청 거절(수신자) 또는 취소(요청자) */
    @DeleteMapping("/requests/{requestId}")
    public ApiResponse<Void> deleteRequest(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long requestId
    ) {
        friendService.deleteRequest(userId, requestId);
        return ApiResponse.ok();
    }

    /** 내 친구 목록 */
    @GetMapping
    public ApiResponse<List<UserBriefDTO>> friends(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.ok(friendService.listFriends(userId));
    }

    /** 친구 삭제 */
    @DeleteMapping("/{otherUserId}")
    public ApiResponse<Void> removeFriend(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long otherUserId
    ) {
        friendService.removeFriend(userId, otherUserId);
        return ApiResponse.ok();
    }
}
