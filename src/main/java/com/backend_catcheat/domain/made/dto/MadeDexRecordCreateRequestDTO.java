package com.backend_catcheat.domain.made.dto;

import java.time.LocalDate;
import java.util.List;

public record MadeDexRecordCreateRequestDTO(
        Long slotId,
        LocalDate loggedOn,
        List<String> imageKeys,
        List<String> foodNames,
        String memo,
        String locationName,
        Double lat,
        Double lng
) {}
