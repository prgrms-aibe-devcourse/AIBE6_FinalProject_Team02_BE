package com.backend_catcheat.domain.my.controller;

import com.backend_catcheat.domain.my.dto.MyProfileResponse;
import com.backend_catcheat.domain.my.dto.NicknameAvailabilityResponse;
import com.backend_catcheat.domain.my.dto.NicknameRequest;
import com.backend_catcheat.domain.my.dto.ProfileImageRequest;
import com.backend_catcheat.domain.my.service.MyService;
import com.backend_catcheat.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "마이페이지", description = """
        내 프로필과 계정. 규칙 두 가지가 있다.

        - **닉네임은 마지막 변경에서 한 달**이 지나야 다시 바꿀 수 있다
        - **탈퇴는 개인정보를 비식별화한 뒤 30일 동안 복구**할 수 있다(소프트 삭제)
        """)
@RestController
@RequestMapping("/api/v1/my")
@RequiredArgsConstructor
public class MyController {
    private final MyService myService;

    /** 마이페이지 프로필 조회 (닉네임 + 변경 가능 여부/가능 시각) */
    @Operation(summary = "내 프로필 조회", description = "닉네임과 함께 **변경 가능 여부·다음 변경 가능 시각**을 준다. 화면이 버튼을 잠그는 데 쓴다.")
    @GetMapping("/profile")
    public ApiResponse<MyProfileResponse> getProfile(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(myService.getProfile(userId));
    }

    /** 닉네임 사용 가능 여부 — 입력 중 실시간 판정 */
    @Operation(summary = "닉네임 사용 가능 확인", description = """
            입력 중 실시간 판정용. 여기서 통과해도 저장 시점에 다른 사람이 먼저 쓸 수 있으므로,
            최종 방어선은 DB UNIQUE 제약이다 — 그 경우 저장 요청이 409 로 돌아온다.
            """)
    @GetMapping("/nickname/availability")
    public ApiResponse<NicknameAvailabilityResponse> checkNickname(
            @AuthenticationPrincipal Long userId,
            @RequestParam String nickname) {
        return ApiResponse.ok(myService.checkAvailability(userId, nickname));
    }

    /**
     * 최초 닉네임 세팅 (온보딩 전)
     */
    @Operation(summary = "최초 닉네임 설정", description = "가입 직후 한 번만 쓴다. 이미 닉네임이 있으면 거절된다.")
    @PostMapping("/nickname")
    public ApiResponse<Void> setInitialNickname(
            @AuthenticationPrincipal Long userId,
            @RequestBody NicknameRequest request) {
        myService.setInitialNickname(userId, request.nickname());
        return ApiResponse.ok();
    }

    /** 닉네임 변경 (마이페이지)
     * 마지막 변경 후 1개월이 지나야 가능 */
    @Operation(summary = "닉네임 변경", description = "**마지막 변경에서 한 달**이 지나야 가능. 이르면 거절된다.")
    @PatchMapping("/nickname")
    public ApiResponse<Void> changeNickname(
            @AuthenticationPrincipal Long userId,
            @RequestBody NicknameRequest request) {
        myService.changeNickname(userId, request.nickname());
        return ApiResponse.ok();
    }

    /** 프로필 사진 설정 (presigned로 업로드된 S3 key 저장) */
    @Operation(summary = "프로필 사진 설정", description = "presigned 로 이미 올린 S3 key 를 저장한다. 사진 자체는 서버를 거치지 않는다.")
    @PatchMapping("/profile-image")
    public ApiResponse<Void> changeProfileImage(
            @AuthenticationPrincipal Long userId,
            @RequestBody ProfileImageRequest request) {
        myService.changeProfileImage(userId, request.key());
        return ApiResponse.ok();
    }

    /** 프로필 사진 제거 → 닉네임 첫 글자 표시로 복귀 */
    @Operation(summary = "프로필 사진 제거", description = "지우면 닉네임 첫 글자 표시로 돌아간다.")
    @DeleteMapping("/profile-image")
    public ApiResponse<Void> removeProfileImage(@AuthenticationPrincipal Long userId) {
        myService.removeProfileImage(userId);
        return ApiResponse.ok();
    }

    /** 회원 탈퇴 (소프트 삭제) */
    @Operation(summary = "회원 탈퇴", description = "개인정보를 비식별화하고 탈퇴 시각을 남긴다. **30일 안에는 복구**할 수 있다.")
    @DeleteMapping
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal Long userId) {
        myService.withdraw(userId);
        return ApiResponse.ok();
    }
}
