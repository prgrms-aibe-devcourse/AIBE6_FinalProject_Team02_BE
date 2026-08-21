package com.backend_catcheat.domain.notification.entity;

public enum NotificationType {

    /** 로그잇 */

    // 로그잇 참여 시
    MADE_DEX_JOINED,

    // 친구가 식단을 업로드 할 시
    FRIEND_CARD_REGISTERED,

    // 댓글 작성 시
    MADE_DEX_COMMENT_ADDED,

    // 댓글 좋아요 시
    MADE_DEX_COMMENT_LIKED,

    // 기록 좋아요 시
    MADE_DEX_RECORD_LIKED,


    /** 챌린짓 */

    // 챌린지 리뷰 작성 시
    CHALLENGE_REVIEW_ADDED,

    // 챌린지내에 해금카드 리뷰 작성 시
    CHALLENGE_CARD_REVIEW_ADDED,

    // 리뷰에 좋아요
    CHALLENGE_REVIEW_LIKED,


    /** 친구 */

    // 친구 요청 시
    FRIEND_REQUEST_RECEIVED,

    // 친구 수락 시
    FRIEND_REQUEST_ACCEPT,

    // 친구 거절 알림
    FRIEND_REQUEST_REJECT,

    /** 관리자가 유저에게 */

    // 승인
    FOOD_REPORT_APPROVE,

    // 거절
    FOOD_REPORT_REJECT,

    // 등록 요청 승인 (검토 대기 카드가 칸에 붙어 해금됨)
    FOOD_REGISTRATION_APPROVE,

    // 등록 요청 거절
    FOOD_REGISTRATION_REJECT,

    /** 관리자에게 가는 알림 */
    // AI가 3번 분석 실패했을 시 관리자 수동 등록 알림
    FOOD_REGISTRATION_REQUEST_RECEIVED,

    // 제보 알림
    FOOD_REPORT_RECEIVED


}
