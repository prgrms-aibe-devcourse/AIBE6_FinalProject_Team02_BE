package com.backend_catcheat.domain.made.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record MadeDexRecordCreateRequestDTO(
        Long slotId,
        LocalDate loggedOn,
        /** 사용자가 적었을 때만 온다. 비면 시각을 남기지 않는다 */
        LocalTime loggedTime,
        List<PhotoInput> photos
) {
    /** 글은 사진마다 붙는다. 기록 전체 메모는 쓰지 않는다 */
    public record PhotoInput(String imageKey, String caption, Double cropX, Double cropY) {}
}
