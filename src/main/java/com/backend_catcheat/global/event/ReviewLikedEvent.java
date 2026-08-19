package com.backend_catcheat.global.event;

public record ReviewLikedEvent (
        Long reviewId,
        Long challengeDexId,
        // 챌린지 식당 리뷰일 때만 값 있음(챌린지 자체 리뷰는 null)
        Long slotId,
        Long actorId,
        Long recipientId
) {

}
