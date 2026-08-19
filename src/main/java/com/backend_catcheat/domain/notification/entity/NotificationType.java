package com.backend_catcheat.domain.notification.entity;

public enum NotificationType {

    /* 로그잇 */

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


    /* 챌린짓 */

    // 챌린지 리뷰 작성 시
    CHALLENGE_REVIEW_ADDED,

    // 챌린지내에 해금카드 리뷰 작성 시
    CHALLENGE_CARD_REVIEW_ADDED,

    // 리뷰에 좋아요
    CHALLENGE_REVIEW_LIKED,


    /* 친구 */

    // 친구 요청 시
    FRIEND_REQUEST_RECEIVED,

    // 친구 수락 시
    FRIEND_REQUEST_ACCEPT,

    // 친구 거절 알림
    FRIEND_REQUEST_REJECT,

    /* 관리자 */

    // 승인
    FOOD_REPORT_APPROVE,

    // 거절
    FOOD_REPORT_REJECT


}
