package com.backend_catcheat.domain.challenge.controller;

import com.backend_catcheat.domain.challenge.dto.*;
import com.backend_catcheat.domain.challenge.entity.ChallengeListStatus;
import com.backend_catcheat.domain.challenge.entity.ChallengeSortType;
import com.backend_catcheat.domain.challenge.entity.MyChallengeRelation;
import com.backend_catcheat.domain.challenge.service.ChallengeParticipationService;
import com.backend_catcheat.domain.challenge.service.ChallengeService;
import com.backend_catcheat.global.common.ApiResponse;
import com.backend_catcheat.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "챌린짓 · 도감", description = """
        개설자가 음식 목록을 정하고 여러 명이 참여하는 시즌 경쟁 도감.

        해금은 **사진 + 좌표**로 인증한다. 서버가 참여자·슬롯 소속·중복·기간을 다시 확인하고,
        마지막으로 슬롯 좌표와의 거리를 잰다(허용 반경 80m).
        탐색 목록의 랭킹은 **최근 7일** 창에서 조회·참여·해금 세 축으로 매긴다.
        """)
@RestController
@RequestMapping("/api/v1/challenges")
@RequiredArgsConstructor
public class ChallengeController {

    private final ChallengeService challengeService;
    private final ChallengeParticipationService challengeParticipationService;
    //개설권 조회
    @Operation(summary = "개설권 잔여 조회", description = "남은 챌린지 개설 가능 횟수.")
    @GetMapping("/creation-tickets")
    public ApiResponse<CreationTicketResponseDTO> creationTickets(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.ok(challengeService.getRemainingTickets(userId));
    }

    @Operation(summary = "챌린지 개설", description = """
            음식 슬롯과 기간을 정해 챌린지를 만든다.
            보상 뱃지를 붙이려면 먼저 보상 뱃지를 만들고 그 `badgeId` 를 넘긴다 — 운영진 프리셋으로 고정하지 않고 개설자가 정한다.
            """)
    @PostMapping
    public ApiResponse<ChallengeCreateResponseDTO> create(
            @AuthenticationPrincipal Long userId,
            @RequestBody ChallengeCreateRequestDTO request
    ){
        return ApiResponse.ok(challengeService.create(userId, request));
    }

    @Operation(summary = "챌린지 참여", description = "참여자로 등록한다. 종료된 챌린지에는 참여할 수 없다.")
    @PostMapping("/{challengeId}/participants")
    public ApiResponse<JoinResponseDTO> join(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long challengeId) {
        return ApiResponse.ok(new JoinResponseDTO(challengeParticipationService.join(userId, challengeId)));
    }

    //해금
    @Operation(summary = "슬롯 해금 (위치 인증)", description = """
            사진(`imageKey`)과 현재 좌표(`lat`·`lng`)로 한 칸을 인증한다. 통과해야 하는 검사는 다섯이다.

            1. 이 챌린지의 참여자인가 (완료 판정 동시성 때문에 참여자 행을 잠근 채 진행)
            2. 이 챌린지의 슬롯인가
            3. 이미 인증한 칸은 아닌가
            4. 챌린지가 끝나지는 않았는가
            5. **슬롯 좌표에서 80m 이내인가**

            전부 통과하면 저장하고, 모든 슬롯을 채웠으면 완주 처리와 보상 지급 이벤트가 함께 나간다.
            응답에는 해금 수·전체 수·완주 여부가 담긴다.
            """)
    @PostMapping("/{challengeId}/unlocks")
    public ApiResponse<UnlockResponseDTO> unlock(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long challengeId,
            @RequestBody UnlockRequestDTO request
            ) {
        return ApiResponse.ok(challengeParticipationService.unlock(
                userId, challengeId, request.slotId(), request.imageKey(),
                request.lat(), request.lng()));
    }

    //탐색 (정렬 + 페이지)
    @Operation(summary = "챌린지 탐색 (랭킹 정렬)", description = """
            진행중 챌린지를 정렬해 한 페이지씩 돌려준다.

            - `sort=LATEST` 최신순 · `VIEWS` 최근 7일 조회 · `PARTICIPANTS` 최근 7일 신규 참여 · `UNLOCKS` 최근 7일 해금
            - 집계·정렬·페이징을 DB 에서 끝내므로 응답시간이 전체 건수에 비례하지 않는다
            - 동점은 최신순으로 확정한다 — 순서가 흔들리면 페이지 경계에서 항목이 겹치거나 빠진다
            """)
    @GetMapping
    public ApiResponse<PageResponse<ChallengeSummaryDTO>> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "ONGOING") ChallengeListStatus status,
            @RequestParam(defaultValue = "LATEST") ChallengeSortType sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ){
        return ApiResponse.ok(challengeService.getChallenges(userId, status, sort, page, size));
    }

    //상세보기
    @Operation(summary = "챌린지 상세 조회", description = "슬롯 목록과 내 진행 상태를 함께 돌려준다. 상세 진입은 조회수로 집계돼 랭킹에 반영된다.")
    @GetMapping("/{challengeId}")
    public ApiResponse<ChallengeDetailResponseDTO> detail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long challengeId) {
        return ApiResponse.ok(challengeService.getDetail(userId, challengeId));
    }
    //내 챌린지 (개설한 / 참여 중 / 완료한)
    @Operation(summary = "내 챌린지 목록", description = "`relation` 으로 개설한 것 · 참여 중 · 완료한 것을 나눠 조회한다.")
    @GetMapping("/mine")
    public ApiResponse<List<ChallengeSummaryDTO>> myChallenges(
            @AuthenticationPrincipal Long userId,
            @RequestParam MyChallengeRelation relation
    ){
        return ApiResponse.ok(challengeService.getMyChallenges(userId, relation));
    }

    //챌린지 포기(나가기)
    @Operation(summary = "챌린지 포기", description = "내 참여와 인증 기록을 지운다.")
    @DeleteMapping("/{challengeId}/participants")
    public ApiResponse<Void> leave(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long challengeId) {
        challengeParticipationService.leave(userId, challengeId);
        return ApiResponse.ok();
    }

    //챌린지 삭제 (개설자만)
    @Operation(summary = "챌린지 삭제", description = "개설자만 가능. 슬롯·참여자·해금·조회수·리뷰가 함께 정리된다.")
    @DeleteMapping("/{challengeId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long challengeId) {
        challengeService.delete(userId, challengeId);
        return ApiResponse.ok();
    }

    //챌린지 수동 종료 (개설자만)
    @Operation(summary = "챌린지 수동 종료", description = "개설자만 가능. 기간이 남아 있어도 더 이상 해금할 수 없게 만든다.")
    @PostMapping("/{challengeId}/close")
    public ApiResponse<Void> close(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long challengeId) {
        challengeService.close(userId, challengeId);
        return ApiResponse.ok();
    }

    //챌린지 이름 검색 (무한스크롤)
    @Operation(summary = "챌린지 이름 검색", description = "무한 스크롤용. `size` 가 한 번에 불러올 개수다.")
    @GetMapping("/search")
    public ApiResponse<PageResponse<ChallengeSummaryDTO>> search(
            @AuthenticationPrincipal Long userId,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size //스크롤 개수 단위
    ){
        return ApiResponse.ok(challengeService.search(userId, keyword, page, size));
    }

}
