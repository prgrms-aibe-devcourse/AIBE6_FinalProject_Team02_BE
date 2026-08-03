package com.backend_catcheat.domain.challenge.controller;

import com.backend_catcheat.domain.challenge.dto.*;
import com.backend_catcheat.domain.challenge.entity.ChallengeListStatus;
import com.backend_catcheat.domain.challenge.service.ChallengeParticipationService;
import com.backend_catcheat.domain.challenge.service.ChallengeService;
import com.backend_catcheat.global.common.ApiResponse;
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
    @PostMapping("/{challengeId}//unlocks")
    public ApiResponse<UnlockResponseDTO> unlock(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long challengeId,
            @RequestParam UnlockRequestDTO request
            ) {
        return ApiResponse.ok(challengeParticipationService.unlock(
                userId, challengeId, request.slotId(), request.imageKey()));
    }

    //탐색
    @GetMapping
    public ApiResponse<List<ChallengeSummaryDTO>> list(
            @RequestParam(defaultValue = "ONGOING")ChallengeListStatus status
            ){
        return ApiResponse.ok(challengeService.getChallenges(status));
    }
    
    //상세보기
    @GetMapping("/{challengeId}")
    public ApiResponse<ChallengeDetailResponseDTO> detail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long challengeId) {
        return ApiResponse.ok(challengeService.getDetail(userId, challengeId));
    }


}
