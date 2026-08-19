package com.backend_catcheat.global.event;

public record FriendRequestAcceptedEvent (
        Long requestId,
        Long actorId,
        Long recipientId
){
}
