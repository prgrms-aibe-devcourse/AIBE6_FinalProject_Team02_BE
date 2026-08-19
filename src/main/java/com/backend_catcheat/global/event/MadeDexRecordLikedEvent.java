package com.backend_catcheat.global.event;

public record MadeDexRecordLikedEvent (
        Long recordId,
        Long madeDexId,
        Long actorId,
        Long recipientId
){
}
