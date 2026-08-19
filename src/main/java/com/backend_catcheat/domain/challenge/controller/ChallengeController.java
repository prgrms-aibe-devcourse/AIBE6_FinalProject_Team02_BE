package com.backend_catcheat.domain.challenge.controller;

import com.backend_catcheat.domain.challenge.dto.*;
import com.backend_catcheat.domain.challenge.entity.ChallengeListStatus;
import com.backend_catcheat.domain.challenge.entity.ChallengeSortType;
import com.backend_catcheat.domain.challenge.entity.MyChallengeRelation;
import com.backend_catcheat.domain.challenge.service.ChallengeParticipationService;
import com.backend_catcheat.domain.challenge.service.ChallengeService;
import com.backend_catcheat.global.common.ApiResponse;
import com.backend_catcheat.global.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/challenges")
@RequiredArgsConstructor
public class ChallengeController {

    private final ChallengeService challengeService;
    private final ChallengeParticipationService challengeParticipationService;
    //개설권 조회
    @GetMapping("/creation-tickets")
    public ApiResponse<CreationTicketResponseDTO> creationTickets(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.ok(challengeService.getRemainingTickets(userId));
    }

    @PostMapping
    public ApiResponse<ChallengeCreateResponseDTO> create(
            @AuthenticationPrincipal Long userId,
            @RequestBody ChallengeCreateRequestDTO request
    ){
        return ApiResponse.ok(challengeService.create(userId, request));
    }

    @PostMapping("/{challengeId}/participants")
    public ApiResponse<JoinResponseDTO> join(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long challengeId) {
        return ApiResponse.ok(new JoinResponseDTO(challengeParticipationService.join(userId, challengeId)));
    }

    //해금
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
    @GetMapping("/{challengeId}")
    public ApiResponse<ChallengeDetailResponseDTO> detail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long challengeId) {
        return ApiResponse.ok(challengeService.getDetail(userId, challengeId));
    }
    //내 챌린지 (개설한 / 참여 중 / 완료한)
    @GetMapping("/mine")
    public ApiResponse<List<ChallengeSummaryDTO>> myChallenges(
            @AuthenticationPrincipal Long userId,
            @RequestParam MyChallengeRelation relation
    ){
        return ApiResponse.ok(challengeService.getMyChallenges(userId, relation));
    }

    //챌린지 포기(나가기)
    @DeleteMapping("/{challengeId}/participants")
    public ApiResponse<Void> leave(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long challengeId) {
        challengeParticipationService.leave(userId, challengeId);
        return ApiResponse.ok();
    }

    //챌린지 삭제 (개설자만)
    @DeleteMapping("/{challengeId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long challengeId) {
        challengeService.delete(userId, challengeId);
        return ApiResponse.ok();
    }

    //챌린지 수동 종료 (개설자만)
    @PostMapping("/{challengeId}/close")
    public ApiResponse<Void> close(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long challengeId) {
        challengeService.close(userId, challengeId);
        return ApiResponse.ok();
    }

    //챌린지 이름 검색 (무한스크롤)
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
