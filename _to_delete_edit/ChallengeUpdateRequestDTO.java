package com.backend_catcheat.domain.challenge.dto;

/**
 * 챌린지 수정 요청(부분 수정).
 * - name: 비어있지 않으면 변경, null/blank면 유지
 * - description: null이 아니면 변경(""이면 소개글 비우기), null이면 유지
 * - imageKey: null이 아니면 대표 이미지 교체(옛 이미지는 S3에서 정리), null이면 유지
 * 기간/슬롯은 정합성 위험으로 이 API에서 다루지 않는다.
 */
public record ChallengeUpdateRequestDTO(
        String name,
        String description,
        String imageKey
) {}
