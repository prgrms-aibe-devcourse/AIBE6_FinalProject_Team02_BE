package com.backend_catcheat.global.event;

/**
 * 참여자가 챌린지를 완료(모든 슬롯 해금)했을 때 발생하는 이벤트
 */
public record ChallengeCompletedEvent(Long userId, Long rewardBadgeId) {}
