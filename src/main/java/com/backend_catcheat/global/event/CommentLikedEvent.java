package com.backend_catcheat.global.event;

public record CommentLikedEvent (
        Long commentId,
        Long madeDexRecordId,
        Long madeDexId,
        Long actorId,
        Long recipientId
){
}
