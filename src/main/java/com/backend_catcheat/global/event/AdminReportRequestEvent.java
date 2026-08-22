package com.backend_catcheat.global.event;

public record AdminReportRequestEvent (
        Long reporterId,
        String foodName,
        Long reportId
){


}
