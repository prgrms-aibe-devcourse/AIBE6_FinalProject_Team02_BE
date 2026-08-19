package com.backend_catcheat.global.event;

public record MadeDexMemberJoinedEvent (
        Long madeDexId,
        String madeDexName,
        Long actorId,
        Long recipientId
) {}
