package com.backend_catcheat.global.event;

public record FriendRequestSentEvent (
        Long requestId,
        Long actorId,
        Long recipientId
){
}
