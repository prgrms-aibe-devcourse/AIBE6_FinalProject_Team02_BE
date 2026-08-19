package com.backend_catcheat.global.event;

public record MadeDexRecordUploadedEvent (
    Long recordId,
    Long madeDexId,
    Long actorId,
    Long recipientId
){
}
