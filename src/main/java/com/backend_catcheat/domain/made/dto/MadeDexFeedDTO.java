package com.backend_catcheat.domain.made.dto;

import java.time.LocalDate;
import java.util.List;

public record MadeDexFeedDTO(
        LocalDate date,
        List<MadeDexFeedSlotDTO> slots
) {}
