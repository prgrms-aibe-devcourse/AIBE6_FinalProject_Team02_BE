package com.backend_catcheat.global.event;

public record CommentCreatedEvent (
        Long commentId,
        Long madeDexRecordId,
        Long madeDexId,
        Long actorId,
        Long recipientId
){
}
