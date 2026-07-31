package com.backend_catcheat.global.event;

/**
 * 신규 회원 가입(소셜 최초 로그인) 후 발행되는 이벤트
 */
public record UserRegisteredEvent(Long userId) {
}
