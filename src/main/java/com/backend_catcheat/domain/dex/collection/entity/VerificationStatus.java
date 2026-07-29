package com.backend_catcheat.domain.dex.collection.entity;

/**
 * 카드의 검증 상태. **값은 이 둘뿐이다** — §5.2가 못박은 사항이라 임의로 추가하지 않는다.
 */
public enum VerificationStatus {
    /** AI가 사진과 음식 이름의 일치를 확인했다 */
    PHOTO_VERIFIED,
    /** 재분석 상한을 넘겨 수동으로 등록했다. 사진이 증빙으로 관리자 검토 큐에 있다 */
    MANUAL_PENDING
}
