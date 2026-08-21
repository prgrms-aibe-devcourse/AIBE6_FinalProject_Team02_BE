package com.backend_catcheat.global.event;

public record AdminRegistrationRequestEvent(
    Long requesterId,
    String foodName,
    Long requestId
){
}
