package com.backend_catcheat.global.event;

public record FriendRequestRejectedEvent (
        Long requestId,
        Long actorId,
        Long recipientId
){
}
