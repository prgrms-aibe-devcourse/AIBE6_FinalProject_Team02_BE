package com.backend_catcheat.domain.illustration.entity;

// 프롬프트 파일은 자리로만 갈린다. 소재(음식·인물·사물)로는 갈리지 않는다
public enum IllustrationPurpose {

    LOGIT_COVER,
    CHALLENGE_COVER,
    CHALLENGE_SLOT,
    // 유일하게 사진 없이 설명만으로도 만들 수 있다
    BADGE;

    public boolean isBadge() {
        return this == BADGE;
    }
}
