package com.backend_catcheat.global.event;

/**
 * 제작 도감 개설 후 발행되는 이벤트 (첫 제작 도감 뱃지 지급 트리거)
 */
public record MadeDexCreatedEvent(Long userId) {
}
