package com.backend_catcheat.global.event;

/**
 * 기본 도감 슬롯이 해금(수집)된 뒤 발행되는 이벤트
 */
public record SlotsUnlockedEvent(Long userId) {
}
