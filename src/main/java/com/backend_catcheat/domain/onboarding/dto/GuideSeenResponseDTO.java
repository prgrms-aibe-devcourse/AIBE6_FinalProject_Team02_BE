package com.backend_catcheat.domain.onboarding.dto;

import java.util.List;

/** 이 사용자가 본 가이드 키 목록 */
public record GuideSeenResponseDTO(List<String> seen) {
}
