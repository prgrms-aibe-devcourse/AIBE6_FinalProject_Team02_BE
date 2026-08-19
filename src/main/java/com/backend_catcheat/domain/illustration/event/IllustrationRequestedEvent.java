package com.backend_catcheat.domain.illustration.event;

// 엔티티가 아니라 id만 싣는다. 받는 쪽이 다른 스레드라 영속성 컨텍스트를 공유하지 않는다
public record IllustrationRequestedEvent(Long jobId) {
}
