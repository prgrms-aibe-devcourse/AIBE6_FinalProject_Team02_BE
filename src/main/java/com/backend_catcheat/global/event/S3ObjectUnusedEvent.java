package com.backend_catcheat.global.event;

/**
 * 더 이상 참조되지 않는 S3 객체. 커밋 이후에 지운다
 */
public record S3ObjectUnusedEvent(String objectKey) {
}
