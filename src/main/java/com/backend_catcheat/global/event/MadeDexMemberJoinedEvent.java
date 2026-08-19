package com.backend_catcheat.global.event;

public record MadeDexMemberJoinedEvent (
        Long madeDexId,
        Long actorId,
        Long recipientId
) {}
