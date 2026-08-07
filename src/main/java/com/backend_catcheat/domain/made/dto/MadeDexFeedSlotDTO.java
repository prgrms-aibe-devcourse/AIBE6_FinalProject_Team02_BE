package com.backend_catcheat.domain.made.dto;

import java.util.List;

public record MadeDexFeedSlotDTO(
        Long slotId,
        String name,
        boolean hidden,
        List<MadeDexFeedCardDTO> cards
) {}
