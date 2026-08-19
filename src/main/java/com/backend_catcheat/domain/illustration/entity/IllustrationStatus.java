package com.backend_catcheat.domain.illustration.entity;

public enum IllustrationStatus {

    GENERATING,
    DONE,
    // 같은 사진으로 다시 시도해도 결과가 같다. 재시도 버튼을 내지 않는다
    REJECTED,
    FAILED
}
